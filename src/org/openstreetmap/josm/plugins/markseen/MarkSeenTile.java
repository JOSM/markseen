package org.openstreetmap.josm.plugins.markseen;

import java.lang.ref.WeakReference;

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
            // we're mimicking the drawing of the underlying tile image, so drawing with an unspecified size should
            // draw the mask at the size of that tile
            width = height = source.getTileSize();
        }
        g.drawImage(mask_, x, y, width, height, null);

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
