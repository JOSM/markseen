package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;

import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class QuadTreeNodeTestSingleRect extends BaseTest {
    @Mock private MarkSeenTileController tileController;
    @Mock private TileSource tileSource;

    private IndexColorModel indexColorModel;
    private BufferedImage EMPTY_MASK;
    private BufferedImage FULL_MASK;
    private int tileSize = 256;

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Before
    public void setUp() {
        Mockito.when(this.tileSource.getTileSize()).thenReturn(this.tileSize);

        this.indexColorModel = new IndexColorModel(
            1,
            2,
            new byte[]{(byte)0, (byte)255},
            new byte[]{(byte)0, (byte)0},
            new byte[]{(byte)0, (byte)255},
            new byte[]{(byte)0, (byte)128}
        );
        this.EMPTY_MASK = new MarkSeenTileController.WriteInhibitedBufferedImage(
            tileSize,
            tileSize,
            BufferedImage.TYPE_BYTE_BINARY,
            this.indexColorModel,
            new Color(0,0,0,0)
        );
        this.FULL_MASK = new MarkSeenTileController.WriteInhibitedBufferedImage(
            tileSize,
            tileSize,
            BufferedImage.TYPE_BYTE_BINARY,
            this.indexColorModel,
            new Color(255,255,255,255)
        );

        Mockito.when(this.tileController.getTileSource()).thenReturn(this.tileSource);
        Mockito.when(this.tileController.getMaskColorModel()).thenReturn(this.indexColorModel);
        Mockito.when(this.tileController.getEmptyMask()).thenReturn(this.EMPTY_MASK);
        Mockito.when(this.tileController.getFullMask()).thenReturn(this.FULL_MASK);
        Mockito.when(this.tileController.getQuadTreeRWLock()).thenReturn(this.lock);
    }

    @Test
    public void test() throws IOException {
        QuadTreeNode quadTreeRoot = new QuadTreeNode(this.tileController);
        this.lock.writeLock().lock();
        quadTreeRoot.markRectSeen(new Bounds(51.36, -0.35, 51.61, 0.10), 4, this.tileController);
        quadTreeRoot.checkIntegrity();
        BufferedImage result;
        byte[] loadedByteArray;

        result = quadTreeRoot.getNodeForTile(
            50,
            50,
            13,
            true,
            this.tileController
        ).getMask(true, this.tileController);
        quadTreeRoot.checkIntegrity();
        assertArrayEquals(
            ((DataBufferByte) result.getData().getDataBuffer()).getData(),
            ((DataBufferByte) this.EMPTY_MASK.getData().getDataBuffer()).getData()
        );

        result = quadTreeRoot.getNodeForTile(
            8184,
            5448,
            14,
            true,
            this.tileController
        ).getMask(true, this.tileController);
        quadTreeRoot.checkIntegrity();
        assertArrayEquals(
            ((DataBufferByte) result.getData().getDataBuffer()).getData(),
            ((DataBufferByte) this.FULL_MASK.getData().getDataBuffer()).getData()
        );

        result = quadTreeRoot.getNodeForTile(
            1023,
            681,
            11,
            true,
            this.tileController
        ).getMask(true, this.tileController);
        quadTreeRoot.checkIntegrity();
        assertArrayEquals(
            ((DataBufferByte) result.getData().getDataBuffer()).getData(),
            ((DataBufferByte) this.FULL_MASK.getData().getDataBuffer()).getData()
        );

        result = quadTreeRoot.getNodeForTile(
            1024,
            680,
            11,
            true,
            this.tileController
        ).getMask(true, this.tileController);
        quadTreeRoot.checkIntegrity();
        assertArrayEquals(
            ((DataBufferByte) result.getData().getDataBuffer()).getData(),
            byteArrayFromResource("QuadTreeNodeTest/testSingleRect/0/11-1024-680.bin")
        );

        this.lock.writeLock().unlock();
    }
}
