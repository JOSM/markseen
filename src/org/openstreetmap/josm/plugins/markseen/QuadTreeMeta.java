package org.openstreetmap.josm.plugins.markseen;

import java.lang.Runnable;
import java.lang.Thread;
import java.lang.Throwable;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

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
            super(1, 1, 5, java.util.concurrent.TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(16));
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
        private final boolean checkIntegrity;

        public MarkBoundsSeenRequest(Bounds bounds_, double minTilesAcross_, boolean checkIntegrity_) {
            this.bounds = bounds_;
            this.minTilesAcross = minTilesAcross_;
            this.checkIntegrity = checkIntegrity_;
        }

        @Override
        public void run() {
            try {
                QuadTreeMeta.this.quadTreeRoot.markBoundsSeen(this.bounds, this.minTilesAcross);
            } catch (QuadTreeNode.ExtremeAspectRatioException e) {
                Logging.warn(e.getMessage());
            }
            if (this.checkIntegrity) {
                QuadTreeMeta.this.quadTreeRoot.checkIntegrity();
            }
        }
    }

    private class ClearRequest implements Runnable {
        private final boolean checkIntegrity;

        public ClearRequest(boolean checkIntegrity_) {
            this.checkIntegrity = checkIntegrity_;
        }

        @Override
        public void run() {
            QuadTreeMeta.this.quadTreeRoot.clear();
        }
    }

    interface QuadTreeModifiedListener {
        void quadTreeModified();
    }

    // while it would be perfectly possible to perform optimize calls in the QuadTreeEditExecutor thread -
    // the two operations can't run concurrently, doing so would introduce some weirdness arising from it
    // being used to handle both "real" edits and "edits" that have no visible effect.
    private class QuadTreeOptimizeExecutor extends ThreadPoolExecutor implements QuadTreeModifiedListener {
        public QuadTreeOptimizeExecutor() {
            super(1, 1, 2, java.util.concurrent.TimeUnit.MINUTES, new SynchronousQueue<Runnable>());
            this.allowCoreThreadTimeOut(true);
            this.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        }

        @Override
        public void beforeExecute(Thread thread, Runnable runnable) {
            // we really shouldn't already have the lock but let's not risk double-locking
            while (!QuadTreeMeta.this.quadTreeRWLock.isWriteLockedByCurrentThread()) {
                try {
                    // this sleep should block the submission of additional optimize operations until it
                    // has completed - those submissions should just "fail" silently without issue. we
                    // shouldn't have to worry about further operations being submitted while the actual
                    // task is running because it occupies the lock that the generator of these events
                    // would need itself.
                    Thread.sleep(Config.getPref().getInt("markseen.autoOptimizeDelayMS", 30000));
                    QuadTreeMeta.this.quadTreeRWLock.writeLock().lockInterruptibly();
                } catch (InterruptedException e) {
                    // we'll just loop around and start a wait again - we can't cancel the execution of
                    // this task from here and it's not safe to continue without the lock
                }
            }
        }

        @Override
        public void afterExecute(Runnable runnable, Throwable throwable) {
            // we should only have acquired the lock once but let's not risk not being completely unlocked
            while (QuadTreeMeta.this.quadTreeRWLock.isWriteLockedByCurrentThread()) {
                QuadTreeMeta.this.quadTreeRWLock.writeLock().unlock();
            }
        }

        public void quadTreeModified() {
            this.execute(() -> {
                try {
                    QuadTreeMeta.this.quadTreeRoot.optimize(true);
                    Logging.debug("QuadTreeMeta completed optimize() run");
                } catch (InterruptedException e) {
                    Logging.debug("QuadTreeMeta optimize() interrupted");
                }
            });
        }

        // unlike QuadTreeEditExecutor it's not the end of the world if we somehow get >1 threads in the
        // pool, it just won't be useful
    }

    // we don't worry about using the specific configured color ("maskColor") for marking regions, instead just expect
    // the palette to match opaque white to that color and transparent black to the "background" color
    protected final static Color UNMARK_COLOR = new Color(0, 0, 0, 0);
    protected final static Color MARK_COLOR = new Color(255, 255, 255, 255);

    public final ReentrantReadWriteLock quadTreeRWLock = new ReentrantReadWriteLock();

    protected IndexColorModel maskColorModel;

    protected final int tileSize;
    protected final Color maskColor;
    protected final double maskOpacity;

    protected final BufferedImage EMPTY_MASK;
    protected final BufferedImage FULL_MASK;

    private final ThreadPoolExecutor quadTreeEditExecutor;
    private final QuadTreeOptimizeExecutor quadTreeOptimizeExecutor;

    private final Set<QuadTreeModifiedListener> modifiedListeners;

    public final QuadTreeNode quadTreeRoot;

    public QuadTreeMeta(int tileSize_, Color maskColor_, double maskOpacity_, boolean autoOptimize) {
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
        this.quadTreeOptimizeExecutor = new QuadTreeOptimizeExecutor();
        this.modifiedListeners = Collections.synchronizedSet(new HashSet<QuadTreeModifiedListener>());

        if (autoOptimize) {
            this.modifiedListeners.add(this.quadTreeOptimizeExecutor);
        }
    }

    public void requestSeenBoundsMark(Bounds bounds, double minTilesAcross) {
        this.requestSeenBoundsMark(bounds, minTilesAcross, false);
    }

    public void requestSeenBoundsMark(Bounds bounds, double minTilesAcross, boolean checkIntegrity) {
        this.quadTreeEditExecutor.execute(new MarkBoundsSeenRequest(bounds, minTilesAcross, checkIntegrity));
    }

    public void requestClear() {
        this.requestClear(false);
    }

    public void requestClear(boolean checkIntegrity) {
        this.quadTreeEditExecutor.execute(new ClearRequest(checkIntegrity));
    }

    protected long getEditRequestQueueCompletedTaskCount() {
        return this.quadTreeEditExecutor.getCompletedTaskCount();
    }

    /**
     *  A word of caution - handlers could be called from any thread
     */
    public void addModifiedListener(QuadTreeModifiedListener modifiedListener) {
        this.modifiedListeners.add(modifiedListener);
    }
}
