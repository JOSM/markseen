package org.openstreetmap.josm.plugins.markseen;

import java.awt.image.BufferedImage;

import java.lang.ref.WeakReference;
import java.util.function.Function;

import org.openstreetmap.gui.jmapviewer.Tile;


/**
*  The idea of this being a "dynamic reference" is that, given a tile's x/y/z, could keep a memo to that QuadTreeNode
*  while still allowing it to be garbage-collected if it became defunct. In such a case it would be able to re-fetch
*  a current QuadTreeNode.
*/
class QuadTreeNodeDynamicReference {
    private WeakReference<QuadTreeNode> quadTreeNodeMemo;
    private final QuadTreeMeta quadTreeMeta;
    private final Tile tile;

    public QuadTreeNodeDynamicReference(QuadTreeMeta quadTreeMeta_, Tile tile_) {
        this.quadTreeMeta = quadTreeMeta_;
        this.tile = tile_;
    }

    public QuadTreeNode getQuadTreeNode(boolean write) {
        QuadTreeNode node;
        if (this.quadTreeNodeMemo != null) {
            node = this.quadTreeNodeMemo.get();
            if (node != null) {
                return node;
            }
        }
        node = this.quadTreeMeta.quadTreeRoot.getNodeForTile(
            this.tile.getXtile(),
            this.tile.getYtile(),
            this.tile.getZoom(),
            write
        );
        if (node == null) {
            // there's nothing more we can do without write access
            return null;
        }
        this.quadTreeNodeMemo = new WeakReference<QuadTreeNode>(node);
        return node;
    }

    public <R> R maskReadOperation(Function<BufferedImage,R> operation) {
        return this.maskReadOperation(operation, false);
    }

    /**
     *  Performs minimal amount of locking required to be able to perform `operation`, a function which accepts the
     *  current QuadTreeNode mask as an argument and releases the lock(s) afterwards.
     */
    public <R> R maskReadOperation(Function<BufferedImage,R> operation, boolean checkIntegrity) {
        // attempt with read-lock first
        this.quadTreeMeta.quadTreeRWLock.readLock().lock();
        QuadTreeNode node = this.getQuadTreeNode(false);
        if (node == null) {
            // operation could not be performed with only a read-lock, we'll have to drop the read-lock and
            // reacquire with write lock so that any required resources can be created or modified
            this.quadTreeMeta.quadTreeRWLock.readLock().unlock();
            this.quadTreeMeta.quadTreeRWLock.writeLock().lock();
            node = this.getQuadTreeNode(true);
        }

        // if we already have the write-lock we won't drop it - it's likely we'll need the write-lock to perform
        // getMask if this tile didn't previously have a valid quadTreeNodeMemo
        BufferedImage mask_ = node.getMask(
            this.quadTreeMeta.quadTreeRWLock.isWriteLockedByCurrentThread(),
            this.quadTreeMeta.quadTreeRWLock.isWriteLockedByCurrentThread()
        );
        if (mask_ == null) {
            // this should only have been possible if we hadn't already taken the write-lock
            assert !this.quadTreeMeta.quadTreeRWLock.isWriteLockedByCurrentThread();
            // operation could not be performed with only a read-lock, we'll have to drop the read-lock and
            // reacquire with write lock so that any required resources can be created or modified
            this.quadTreeMeta.quadTreeRWLock.readLock().unlock();
            this.quadTreeMeta.quadTreeRWLock.writeLock().lock();
            mask_ = node.getMask(true, true);
        }

        try {
            R r = operation.apply(mask_);
            if (checkIntegrity) {
                this.quadTreeMeta.quadTreeRoot.checkIntegrity();
            }
            return r;
        } finally {
            // release whichever lock we had
            if (this.quadTreeMeta.quadTreeRWLock.isWriteLockedByCurrentThread()) {
                this.quadTreeMeta.quadTreeRWLock.writeLock().unlock();
            } else {
                this.quadTreeMeta.quadTreeRWLock.readLock().unlock();
            }
        }
    }
}
