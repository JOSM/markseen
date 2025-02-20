// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import java.util.concurrent.Callable;

import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;

public class MarkSeenTile extends Tile {
    private final QuadTreeNodeDynamicReference quadTreeNodeDynamicReference;

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
        this.quadTreeNodeDynamicReference = new QuadTreeNodeDynamicReference(quadTreeMeta_, this);
    }

    protected void paintInner(
        final Graphics g,
        final int x,
        final int y,
        final int width,
        final int height,
        final boolean ignoreWH
    ) {
        this.quadTreeNodeDynamicReference.maskReadOperation(mask -> {
            int width_ = width, height_ = height;
            if (ignoreWH) {
                // we're mimicking the drawing of the underlying tile image, so drawing with an unspecified size should
                // draw the mask at the size of that tile
                width_ = height_ = source.getTileSize();
            }
            g.drawImage(mask, x, y, width_, height_, null);
            return null;
        });
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

    protected void paintPlaceholder(Graphics g, int x, int y) {
        super.paint(g, x, y);
    }

    protected void paintPlaceholder(Graphics g, int x, int y, int width, int height) {
        super.paint(g, x, y, width, height);
    }

    /**
     * Verbatim copy from Tile implementation to allow loadPlaceholderFromCache to work
     */
    private static class CachedCallable<V> implements Callable<V> {
        private V result;
        private Callable<V> callable;

        /**
         * Wraps callable so it is evaluated only once
         * @param callable to cache
         */
        CachedCallable(Callable<V> callable) {
            this.callable = callable;
        }

        @Override
        public synchronized V call() {
            try {
                if (result == null) {
                    result = callable.call();
                }
                return result;
            } catch (Exception e) {
                // this should not happen here
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Verbatim copy from Tile implementation with paint call switched out in favour of paintPlaceholder
     */
    @Override
    public void loadPlaceholderFromCache(TileCache cache) {
        /*
         *  use LazyTask as creation of BufferedImage is very expensive
         *  this way we can avoid object creation until we're sure it's needed
         */
        final CachedCallable<BufferedImage> tmpImage = new CachedCallable<>(new Callable<BufferedImage>() {
            @Override
            public BufferedImage call() throws Exception {
                return new BufferedImage(source.getTileSize(), source.getTileSize(), BufferedImage.TYPE_INT_ARGB);
            }
        });

        for (int zoomDiff = 1; zoomDiff < 5; zoomDiff++) {
            // first we check if there are already the 2^x tiles
            // of a higher detail level
            int zoomHigh = zoom + zoomDiff;
            if (zoomDiff < 3 && zoomHigh <= JMapViewer.MAX_ZOOM) {
                int factor = 1 << zoomDiff;
                int xtileHigh = xtile << zoomDiff;
                int ytileHigh = ytile << zoomDiff;
                final double scale = 1.0 / factor;

                /*
                 * use LazyTask for graphics to avoid evaluation of tmpImage, until we have
                 * something to draw
                 */
                CachedCallable<Graphics2D> graphics = new CachedCallable<>(new Callable<Graphics2D>() {
                    @Override
                    public Graphics2D call() throws Exception {
                        Graphics2D g = (Graphics2D) tmpImage.call().getGraphics();
                        g.setTransform(AffineTransform.getScaleInstance(scale, scale));
                        return g;
                    }
                });

                int paintedTileCount = 0;
                for (int x = 0; x < factor; x++) {
                    for (int y = 0; y < factor; y++) {
                        Tile tile = cache.getTile(source, xtileHigh + x, ytileHigh + y, zoomHigh);
                        if (tile != null && tile.isLoaded()) {
                            paintedTileCount++;
                            ((MarkSeenTile) tile).paintPlaceholder(graphics.call(), x * source.getTileSize(), y * source.getTileSize());
                        }
                    }
                }
                if (paintedTileCount == factor * factor) {
                    image = tmpImage.call();
                    return;
                }
            }

            int zoomLow = zoom - zoomDiff;
            if (zoomLow >= JMapViewer.MIN_ZOOM) {
                int xtileLow = xtile >> zoomDiff;
                int ytileLow = ytile >> zoomDiff;
                final int factor = 1 << zoomDiff;
                final double scale = factor;
                CachedCallable<Graphics2D> graphics = new CachedCallable<>(new Callable<Graphics2D>() {
                    @Override
                    public Graphics2D call() throws Exception {
                        Graphics2D g = (Graphics2D) tmpImage.call().getGraphics();
                        AffineTransform at = new AffineTransform();
                        int translateX = (xtile % factor) * source.getTileSize();
                        int translateY = (ytile % factor) * source.getTileSize();
                        at.setTransform(scale, 0, 0, scale, -translateX, -translateY);
                        g.setTransform(at);
                        return g;
                    }

                });

                Tile tile = cache.getTile(source, xtileLow, ytileLow, zoomLow);
                if (tile != null && tile.isLoaded()) {
                    ((MarkSeenTile) tile).paintPlaceholder(graphics.call(), 0, 0);
                    image = tmpImage.call();
                    return;
                }
            }
        }
    }
}
