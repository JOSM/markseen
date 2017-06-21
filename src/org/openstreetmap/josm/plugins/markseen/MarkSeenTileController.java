package org.openstreetmap.josm.plugins.markseen;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileController;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;


public class MarkSeenTileController extends TileController {
    private static IndexColorModel maskColorModel;
    public static IndexColorModel getMaskColorModel() {
        return maskColorModel;
    }

    static {
        maskColorModel = new IndexColorModel(
            1,
            2,
            new byte[]{(byte)0, (byte)255},
            new byte[]{(byte)0, (byte)0},
            new byte[]{(byte)0, (byte)255},
            new byte[]{(byte)0, (byte)128}
        );
    }

    private QuadTreeNode quadTreeRoot;

    public MarkSeenTileController(TileSource source, TileCache tileCache, TileLoaderListener listener) {
        super(source, tileCache, listener);
    }

//     public Tile getTile(int tilex, int tiley, int zoom) {
//         Tile upstreamTile = super.getTile(tilex, tiley, zoom);
//         int upstreamTileSize = upstreamTile.getImage().getWidth();
//         BufferedImage maskImage = new BufferedImage(
//             upstreamTileSize,
//             upstreamTileSize,
//             BufferedImage.TYPE_BYTE_BINARY,
//             maskColorModel
//         );
//         return new Tile(;
//     }

    /**
     * Verbatim copy of TileController.getTile, generating MarkSeenTiles instead of regular Tiles
     */
    @Override
    public Tile getTile(int tilex, int tiley, int zoom) {
        int max = 1 << zoom;
        if (tilex < 0 || tilex >= max || tiley < 0 || tiley >= max)
            return null;
        Tile tile = tileCache.getTile(tileSource, tilex, tiley, zoom);
        if (tile == null) {
            tile = new MarkSeenTile(this, tileSource, tilex, tiley, zoom);
            tileCache.addTile(tile);
            tile.loadPlaceholderFromCache(tileCache);
        }
        if (tile.error) {
            tile.loadPlaceholderFromCache(tileCache);
        }
        if (!tile.isLoaded()) {
            tileLoader.createTileLoaderJob(tile).submit();
        }
        return tile;
    }
}
