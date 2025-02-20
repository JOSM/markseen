// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileController;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;

public class MarkSeenTileController extends TileController {
    public final QuadTreeMeta quadTreeMeta;

    public MarkSeenTileController(
        QuadTreeMeta quadTreeMeta_,
        TileSource source_,
        TileCache tileCache_,
        TileLoaderListener listener_
    ) {
        super(source_, tileCache_, listener_);
        this.quadTreeMeta = quadTreeMeta_;
    }

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
            tile = new MarkSeenTile(this.quadTreeMeta, tileSource, tilex, tiley, zoom);
            tileCache.addTile(tile);
            tile.loadPlaceholderFromCache(tileCache);
        }
        if (tile.hasError()) {
            tile.loadPlaceholderFromCache(tileCache);
        }
        if (!tile.isLoaded()) {
            tileLoader.createTileLoaderJob(tile).submit();
        }
        return tile;
    }
}
