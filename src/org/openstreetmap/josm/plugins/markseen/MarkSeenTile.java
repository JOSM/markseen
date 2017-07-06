package org.openstreetmap.josm.plugins.markseen;

import java.lang.ref.WeakReference;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;

public class MarkSeenTile extends Tile {
    private WeakReference<QuadTreeNode> quadTreeNodeMemo;
    private MarkSeenTileController tileController;

    public MarkSeenTile(MarkSeenTileController controller, TileSource source, int xtile, int ytile, int zoom) {
        this(controller, source, xtile, ytile, zoom, LOADING_IMAGE);
    }

    public MarkSeenTile(
        MarkSeenTileController controller,
        TileSource source,
        int xtile,
        int ytile,
        int zoom,
        BufferedImage image
    ) {
        super(source, xtile, ytile, zoom, image);
        this.tileController = controller;
    }

    private QuadTreeNode getQuadTreeNode(boolean write) {
        QuadTreeNode node;
        if (this.quadTreeNodeMemo != null) {
            node = this.quadTreeNodeMemo.get();
            if (node != null) {
                return node;
            }
        }
        node = this.tileController.getQuadTreeRoot().getNodeForTile(xtile, ytile, zoom, write, this.tileController);
        if (node == null) {
            // there's nothing more we can do without write access
            return null;
        }
        this.quadTreeNodeMemo = new WeakReference<QuadTreeNode>(node);
        return node;
    }

    @Override
    public void paint(Graphics g, int x, int y) {
        super.paint(g, x, y);

        // TODO attempt with read-lock first

        this.tileController.quadTreeRWLock.writeLock().lock();
        BufferedImage mask_ = this.getQuadTreeNode(true).getMask(true, this.tileController);
        g.drawImage(mask_, x, y, null);
        this.tileController.quadTreeRWLock.writeLock().unlock();
    }
}
