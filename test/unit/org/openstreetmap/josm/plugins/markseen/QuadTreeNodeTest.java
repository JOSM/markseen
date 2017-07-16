package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Boolean;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;

import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.junit.Rule;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;


@RunWith(Parameterized.class)
public class QuadTreeNodeTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private MarkSeenTileController tileController;
    @Mock private TileSource tileSource;

    private IndexColorModel indexColorModel;
    private BufferedImage EMPTY_MASK;
    private BufferedImage FULL_MASK;
    private int tileSize = 256;

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private Bounds bounds;
    private double minTilesWidth;
    private Object[][] referenceTiles;

    @Parameters
    public static Collection<Object[]> getParameters() throws IOException {
        return Arrays.asList(new Object[][] {
            {
                new Bounds(51.36, -0.35, 51.61, 0.10),
                4,
                new Object[][] {
                    // zoom, xtile, ytile, expectedMask
                    // the expectedMask can either be a byte[] OR a boolean, true denoting FULL_MASK and false
                    // EMPTY_MASK (we can't reference these directly because they haven't been allocated at this point)
                    { 10, 50, 50, false },
                    { 15, 16365, 10867, false },
                    { 14, 8184, 5448, true },
                    { 11, 1023, 681, true },
                    { 16, 32730, 21762, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/0/16-32730-21762.bin") },
                    { 11, 1024, 680, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/0/11-1024-680.bin") },
                    { 7, 63, 42, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/0/7-63-42.bin") }
                }
            }
        });
    }

    public QuadTreeNodeTest(Bounds bounds_, double minTilesWidth_, Object[][] referenceTiles_) {
        this.bounds = bounds_;
        this.minTilesWidth = minTilesWidth_;
        this.referenceTiles = referenceTiles_;
    }

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
    public void test() {
        QuadTreeNode quadTreeRoot = new QuadTreeNode(this.tileController);
        this.lock.writeLock().lock();
        quadTreeRoot.markRectSeen(this.bounds, this.minTilesWidth, this.tileController);
        quadTreeRoot.checkIntegrity();

        for (int i = 0; i<referenceTiles.length; i++) {
            Object[] referenceTileInfo = this.referenceTiles[i];
            System.out.format("Checking reference tile %d\n", i);
            int zoom = (int)referenceTileInfo[0];
            int tilex = (int)referenceTileInfo[1];
            int tiley = (int)referenceTileInfo[2];
            byte[] maskBytes = Boolean.class.isInstance(referenceTileInfo[3]) ?
                (
                    (DataBufferByte) (
                        ((boolean)referenceTileInfo[3]) ?
                                      this.FULL_MASK :
                                      this.EMPTY_MASK
                    ).getData().getDataBuffer()
                ).getData() :
                (byte[])referenceTileInfo[3];

            BufferedImage result = quadTreeRoot.getNodeForTile(
                tilex,
                tiley,
                zoom,
                true,
                this.tileController
            ).getMask(true, this.tileController);
            quadTreeRoot.checkIntegrity();

//             System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(
//                 ((DataBufferByte) result.getData().getDataBuffer()).getData())
//             );

            assertArrayEquals(
                ((DataBufferByte) result.getData().getDataBuffer()).getData(),
                maskBytes
            );
        }

        this.lock.writeLock().unlock();
    }
}
