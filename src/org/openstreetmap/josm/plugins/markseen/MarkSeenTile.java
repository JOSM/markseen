package org.openstreetmap.josm.plugins.markseen;

import java.lang.ref.WeakReference;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.util.Random;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;

public class MarkSeenTile extends Tile {
    private WeakReference<QuadTreeNode> quadTreeNodeMemo;
    private final QuadTreeMeta quadTreeMeta;

    public MarkSeenTile(QuadTreeMeta quadTreeMeta_, TileSource source_, int xtile_, int ytile_, int zoom_) {
        this(quadTreeMeta_, source_, xtile_, ytile_, zoom_, LOADING_IMAGE);
    }

    public MarkSeenTile(
        QuadTreeMeta quadTreeMeta_,
        TileSource source_,
        int xtile_,
        int ytile_,
        int zoom_,
        BufferedImage image_
    ) {
        super(source_, xtile_, ytile_, zoom_, image_);
        this.quadTreeMeta = quadTreeMeta_;
    }

    protected QuadTreeNode getQuadTreeNode(boolean write) {
        QuadTreeNode node;
        if (this.quadTreeNodeMemo != null) {
            node = this.quadTreeNodeMemo.get();
            if (node != null) {
                return node;
            }
        }
        node = this.quadTreeMeta.quadTreeRoot.getNodeForTile(
            this.xtile,
            this.ytile,
            this.zoom,
            write
        );
        if (node == null) {
            // there's nothing more we can do without write access
            return null;
        }
        this.quadTreeNodeMemo = new WeakReference<QuadTreeNode>(node);
        return node;
    }

    protected void paintInner(Graphics g, int x, int y, int width, int height, boolean ignoreWH) {
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

        if (ignoreWH) {
            g.drawImage(mask_, x, y, null);
        } else {
            g.drawImage(mask_, x, y, width, height, null);
        }

        // release whichever lock we had
        if (this.quadTreeMeta.quadTreeRWLock.isWriteLockedByCurrentThread()) {
            this.quadTreeMeta.quadTreeRWLock.writeLock().unlock();
        } else {
            this.quadTreeMeta.quadTreeRWLock.readLock().unlock();
        }
    }

    @Override
    public void paint(Graphics g, int x, int y) {
        super.paint(g, x, y);
        this.paintInner(g, x, y, 0, 0, true);
    }

    @Override
    public void paint(Graphics g, int x, int y, int width, int height) {
        super.paint(g, x, y, width, height);
        this.paintInner(g, x, y, width, height, false);
    }
}
