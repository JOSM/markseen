package org.openstreetmap.josm.plugins.markseen;

import java.lang.Runnable;
import java.lang.Thread;
import java.lang.Throwable;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

import org.openstreetmap.josm.data.Bounds;

public class QuadTreeMeta {
    // is there a better way to create a properly read-only BufferedImage? don't know. for now, all we want to be able
    // to do is catch potential bugs where something attempts to write to a mask that is intended to be shared &
    // constant
    private static class WriteInhibitedBufferedImage extends BufferedImage {
        public boolean inhibitWrites = false;

        public WriteInhibitedBufferedImage(int width, int height, int imageType, IndexColorModel cm, Color constColor) {
            super(width, height, imageType, cm);
            Graphics2D g = this.createGraphics();
            g.setBackground(constColor);
            g.clearRect(0, 0, width, height);
            this.inhibitWrites = true;
        }

        @Override
        public Graphics2D createGraphics() {
            if (this.inhibitWrites) {
                throw new RuntimeException("Attempt to draw to WriteInhibitedBufferedImage with inhibitWrites set");
            } else {
                return super.createGraphics();
            }
        }

        @Override
        public WritableRaster getWritableTile(int tileX, int tileY) {
            if (this.inhibitWrites) {
                throw new RuntimeException("Attempt to draw to WriteInhibitedBufferedImage with inhibitWrites set");
            } else {
                return super.getWritableTile(tileX, tileY);
            }
        }
    }

    private class QuadTreeEditExecutor extends ThreadPoolExecutor {
        public QuadTreeEditExecutor() {
            super(1, 1, 5, java.util.concurrent.TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(8));
        }

        @Override
        public void beforeExecute(Thread thread, Runnable runnable) {
            if (!QuadTreeMeta.this.quadTreeRWLock.isWriteLockedByCurrentThread()) {
                QuadTreeMeta.this.quadTreeRWLock.writeLock().lock();
            }
        }

        @Override
        public void afterExecute(Runnable runnable, Throwable throwable) {
            if (this.getQueue().isEmpty()) {
                QuadTreeMeta.this.quadTreeRWLock.writeLock().unlock();
                synchronized(QuadTreeMeta.this.modifiedListeners) {
                    for (QuadTreeModifiedListener listener: QuadTreeMeta.this.modifiedListeners) {
                        listener.quadTreeModified();
                    }
                }
            }
            // else we will elide the lock re-acquisition to allow our worker thread to pick the next edit to make
            // immediately. the reason we do this is that any other threads that managed to acquire the write-lock
            // before we could would presumably be involved in the task of painting tiles - if we know we've got a
            // tree-modifying request in the queue there's no point in letting it go ahead and paint something which
            // we're probably going to dirty immediately anyway.
            //
            // we're only able to do this because we know we only have one thread consuming this queue. if we had >1
            // thread there would be a possibility that the Runnable which we observed on the queue gets claimed by
            // a different thread which *didn't* have the write-lock. if that were the last Runnable in the queue it
            // would lose this thread its chance to release this lock (which it could only do by picking up another
            // Runnable and completing that)
        }

        @Override
        public void setMaximumPoolSize(int maximumPoolSize) {
            if (maximumPoolSize > 1) {
                throw new UnsupportedOperationException("QuadTreeEditExecutor cannot have >1 thread");
            }
            super.setMaximumPoolSize(maximumPoolSize);
        }

        @Override
        public void setCorePoolSize(int corePoolSize) {
            if (corePoolSize > 1) {
                throw new UnsupportedOperationException("QuadTreeEditExecutor cannot have >1 thread");
            }
            super.setCorePoolSize(corePoolSize);
        }
    }

    private class MarkBoundsSeenRequest implements Runnable {
        private final Bounds bounds;
        private final double minTilesAcross;

        public MarkBoundsSeenRequest(Bounds bounds_, double minTilesAcross_) {
            this.bounds = bounds_;
            this.minTilesAcross = minTilesAcross_;
        }

        @Override
        public void run() {
            QuadTreeMeta.this.quadTreeRoot.markBoundsSeen(this.bounds, this.minTilesAcross);
        }
    }

    interface QuadTreeModifiedListener {
        void quadTreeModified();
    }
 
    protected final static Color UNMARK_COLOR = new Color(0, 0, 0, 0);
    protected final static Color MARK_COLOR = new Color(255, 255, 255, 255);

    public final ReentrantReadWriteLock quadTreeRWLock = new ReentrantReadWriteLock();

    protected IndexColorModel maskColorModel;

    protected final int tileSize;
    protected final Color maskColor;
    protected final double maskOpacity;

    protected final BufferedImage EMPTY_MASK;
    protected final BufferedImage FULL_MASK;

    private final Executor quadTreeEditExecutor;

    private final Set<QuadTreeModifiedListener> modifiedListeners;

    public QuadTreeNode quadTreeRoot;

    public QuadTreeMeta(int tileSize_, Color maskColor_, double maskOpacity_) {
        this.tileSize = tileSize_;
        this.maskColor = maskColor_;
        this.maskOpacity = maskOpacity_;
        this.maskColorModel = new IndexColorModel(
            1,
            2,
            new byte[]{(byte)0, (byte)this.maskColor.getRed()},
            new byte[]{(byte)0, (byte)this.maskColor.getGreen()},
            new byte[]{(byte)0, (byte)this.maskColor.getBlue()},
            new byte[]{(byte)0, (byte)(this.maskOpacity*255)}
        );

        this.EMPTY_MASK = new WriteInhibitedBufferedImage(
            this.tileSize,
            this.tileSize,
            BufferedImage.TYPE_BYTE_BINARY,
            this.maskColorModel,
            UNMARK_COLOR
        );
        this.FULL_MASK = new WriteInhibitedBufferedImage(
            this.tileSize,
            this.tileSize,
            BufferedImage.TYPE_BYTE_BINARY,
            this.maskColorModel,
            MARK_COLOR
        );

        this.quadTreeRoot = new QuadTreeNode(this);
        this.quadTreeEditExecutor = new QuadTreeEditExecutor();
        this.modifiedListeners = Collections.synchronizedSet(new HashSet<QuadTreeModifiedListener>());
    }

    public void requestSeenBoundsMark(Bounds bounds, double minTilesAcross) {
        this.quadTreeEditExecutor.execute(new MarkBoundsSeenRequest(bounds, minTilesAcross));
    }

    /**
     *  A word of caution - handlers could be called from any thread
     */
    public void addModifiedListener(QuadTreeModifiedListener modifiedListener) {
        this.modifiedListeners.add(modifiedListener);
    }
}
