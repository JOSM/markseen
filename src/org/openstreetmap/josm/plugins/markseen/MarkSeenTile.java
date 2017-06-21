package org.openstreetmap.josm.plugins.markseen;

import java.lang.ref.WeakReference;

import org.openstreetmap.gui.jmapviewer.Tile;

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
        this.tileController = controller;
        super(source, xtile, ytile, zoom, image);
    }
}
