package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Boolean;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;


@Ignore
public class BaseQuadTreeNodeTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock protected MarkSeenTileController tileController;
    @Mock protected TileSource tileSource;

    protected IndexColorModel indexColorModel;
    protected BufferedImage EMPTY_MASK;
    protected BufferedImage FULL_MASK;

    protected ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // parametrized variables
    protected int scenarioIndex;
    protected Integer seenRectOrderSeed;
    protected Integer referenceTileOrderSeed;
    // derived parametrized variables
    protected int tileSize;
    protected Object[][] seenRects;
    protected Object[][] referenceTiles;

    private static Object[][] scenarios;
    protected static Object[][] getTestScenarios() throws IOException {
        // static initializers can't throw checked exceptions, hence this weirdness
        if (scenarios == null) {
            scenarios = new Object[][] {
                {
                    256,
                    new Object [][] {
                        // bounds, minTilesAcross
                        { new Bounds(51.36, -0.35, 51.61, 0.10), 4. }
                    },
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
                },
                {
                    256,
                    new Object [][] {
                        { new Bounds(-24.68, -48.99, -14.9, -45.35), 4. },
                        { new Bounds(-19.39, -52.734375, -17.47, -45.6591797), 6. },
                        { new Bounds(-20.365, -51.152, -20.014, -50.581), 3. }
                    },
                    new Object[][] {
                        { 10, 50, 50, false },
                        { 3, 1, 4, false },
                        { 10, 375, 559, true },
                        { 15, 12018, 17916, true },
                        { 4, 5, 8, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/1/4-5-8.bin") },
                        { 11, 749, 1119, true },
                        { 12, 1490, 2272, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/1/12-1490-2272.bin") },
                        { 14, 5960, 9091, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/1/14-5960-9091.bin") },
                        { 14, 5960, 9092, false },
                        { 0, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/1/0-0-0.bin") },
                        { 16, 23520, 36514, true }
                    }
                },
                {
                    127,
                    new Object [][] {
                        { new Bounds(0., 0., 20., 20.), 7. },
                        { new Bounds(-10., -10., 0., 0.), 5. },
                        { new Bounds(0., 90., 10., 100.), 4. },
                        { new Bounds(-9., 81., 0., 90.), 9. }
                    },
                    new Object[][] {
                        { 10, 50, 50, false },
                        { 10, 512, 512, false },
                        { 10, 511, 511, false },
                        { 10, 512, 511, true },
                        { 10, 511, 512, true },
                        { 7, 96, 64, false },
                        { 7, 95, 63, false },
                        { 7, 96, 63, true },
                        { 7, 95, 64, true },
                        { 0, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/2/0-0-0.bin") }
                    }
                },
                {
                    200,
                    new Object [][] {
                        { new Bounds(80., 12.8, 88., 25.6), 7. },
                        { new Bounds(82., 25.6, 89., 26.6), 3. },
                        { new Bounds(60., -67., 89., -66.), 3. },
                        { new Bounds(-86.9, 10.6, -70., 21.6), 8. }
                    },
                    new Object[][] {
                        { 10, 50, 50, false },
                        { 10, 0, 0, false },
                        { 18, 140500, 0, true },
                        { 12, 1286, 10, true },
                        { 0, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/3/0-0-0.bin") }
                    }
                },
                {
                    256,
                    new Object [][] {
                        { new Bounds(83.008, 10.519, 86.27, 10.97), 3. },
                        { new Bounds(40.56, -111.14, 41.603, -110.926), 3. },
                        { new Bounds(40.6, -111.1, 41.5, -110.95), 3. },
                        { new Bounds(-0.947, 149.27, -0.181, 149.29), 3. },
                        { new Bounds(-13.73, -94.34, 1.842, -94.31), 3. },
                        { new Bounds(62.922, 110.68, 67.624, 114.47), 3. },
                        { new Bounds(-70.711, 88.09, -70.69, 88.12), 3. },
                        { new Bounds(-63.262, -166.86, -61.569, -153.71), 3. },
                        { new Bounds(66.574, -137., 66.5887, -131.649), 3. },
                        { new Bounds(19.349, 8.83, 19.654, 8.93), 3. },
                        { new Bounds(-60.945, 54.83, -57.83, 54.878), 3. },
                        { new Bounds(-25.75, -125.32, -23.47, -125.28), 3. },
                        { new Bounds(2.862, -142.95, 2.875, -137.32), 3. },
                        { new Bounds(1.217, 152.528, 1.382, 153.14), 3. },
                        { new Bounds(2.767, -49.3, 5.744, -18.), 3. },
                        { new Bounds(-11.27, -128.55, 15.666, -124.664), 3. },
                        { new Bounds(84.481, -108.96, 86.207, -107.67), 3. },
                        { new Bounds(-84.49, -118.07, -82.096, -117.37), 3. },
                        { new Bounds(52.9984, -96.69, 86.024, -96.63), 3. },
                        { new Bounds(-46.08, -81.72, -44.403, -38.19), 3. }
                    },
                    new Object[][] {
                        { 10, 50, 50, false },
                        { 0, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/0-0-0.bin") },
                        { 12, 784, 1535, true },
                        { 12, 786, 1535, false },
                        { 11, 392, 767, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/11-392-767.bin") },
                        { 19, 342025, 366377, true },
                        { 11, 1336, 1431, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/11-1336-1431.bin") },
                        { 6, 40, 44, false },
                        { 10, 837, 279, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/10-837-279.bin") },
                        { 6, 7, 16, false },
                        { 3, 0, 1, false },
                        { 2, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/2-0-0.bin") },
                        { 1, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/1-0-0.bin") },
                        { 6, 7, 15, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/4/6-7-15.bin") }
                    }
                },
                {
                    256,
                    new Object [][] {
                        { new Bounds(26.27, -18.23, 26.29, -18.16), 5. },
                        { new Bounds(41.85, -18.37, 41.87, -16.17), 3. },
                        { new Bounds(52.93, -19.05, 53.72,  39.82), 5. },
                        { new Bounds(41.66,   5.79, 46.86,  34.63), 3. },
                        { new Bounds(39.35,  12.61, 67.16,  12.71), 5. },
                        { new Bounds(23.07,   6.38, 29.55,  38.07), 3. },
                        { new Bounds(57.54,  25.25, 65.26,  93.78), 5. },
                        { new Bounds(52.95,   7.95, 53.21,  10.83), 3. },
                        { new Bounds(63.12,  23.42, 82.03,  73.03), 5. },
                        { new Bounds(25.07,  -8.49, 29.52,  -6.77), 3. },
                        { new Bounds(20.91,   7.83, 21.43,   9.18), 5. },
                        { new Bounds(60.83, -16.42, 65.82,   7.17), 3. },
                        { new Bounds(69.56,  -1.93, 69.87,   1.74), 5. },
                        { new Bounds(65.46, -19.96, 66.39, -19.80), 3. },
                        { new Bounds(28.20,  -1.81, 28.22,  -1.79), 5. },
                        { new Bounds(30.89,   2.29, 30.97,  32.69), 3. },
                        { new Bounds(61.89,  -0.76, 61.96,  -0.31), 5. },
                        { new Bounds(20.70,  20.55, 22.20,  70.68), 3. },
                        { new Bounds(37.95,  -4.34, 46.60,  -2.87), 5. },
                        { new Bounds(38.03,   1.29, 73.76,   6.10), 3. },
                        { new Bounds(30.68,  -1.58, 39.55,  -1.44), 5. },
                        { new Bounds(25.20,  -4.21, 59.81,  -2.48), 3. },
                        { new Bounds(58.94,  15.02, 59.33,  43.95), 5. },
                        { new Bounds(21.00,  -4.28, 21.02,  14.14), 3. },
                        { new Bounds(50.21,  -0.42, 50.24,  -0.20), 5. },
                        { new Bounds(68.01, -19.97, 68.05, -18.65), 3. },
                        { new Bounds(31.51,  20.17, 33.73,  20.41), 5. },
                        { new Bounds(62.21,  -6.46, 62.65,  56.45), 3. },
                        { new Bounds(48.42, -19.22, 50.56, -19.02), 5. }
                    },
                    new Object[][] {
                        { 10, 50, 50, false },
                        { 1, 0, 1, false },
                        { 0, 0, 0, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/5/0-0-0.bin") },
                        { 10, 537, 450, true },
                        { 9, 270, 224, false },
                        { 7, 67, 54, true },
                        { 7, 67, 64, false },
                        { 8, 134, 106, true },
                        { 15, 17312, 13455, false },
                        { 15, 17313, 13419, true },
                        { 10, 500, 362, true },
                        { 10, 498, 362, false },
                        { 14, 8768, 5355, true },
                        { 7, 67, 42, false },
                        { 8, 132, 72, true },
                        { 19, 271123, 151470, false },
                        { 10, 504, 332, true },
                        { 14, 8079, 5357, true },
                        // the following tile covers an interesting potential discontinuity due to varying canonical depths
                        { 7, 66, 56, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/5/7-66-56.bin") },
                        { 4, 9, 4, byteArrayFromResource("QuadTreeNodeTest/testSingleRect/5/4-9-4.bin") }
                    }
                }
            };
        }
        return scenarios;
    }

    public BaseQuadTreeNodeTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        this.scenarioIndex = scenarioIndex_;
        this.seenRectOrderSeed = seenRectOrderSeed_;
        this.referenceTileOrderSeed = referenceTileOrderSeed_;

        Object[] scenario = getTestScenarios()[this.scenarioIndex];
        this.tileSize = (int)scenario[0];
        this.seenRects = (Object[][])scenario[1];
        this.referenceTiles = (Object[][])scenario[2];
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

    protected void markRects(QuadTreeNode quadTreeRoot, Integer orderSeed) {
        List<Integer> remapping = IntStream.range(0, this.seenRects.length).boxed().collect(Collectors.toList());
        if (orderSeed != null) {
            Collections.shuffle(remapping, new Random((long)orderSeed));
        }

        for (int i = 0; i<this.seenRects.length; i++) {
            int j = remapping.get(i);
            Object[] seenRectInfo = this.seenRects[j];
            System.out.format("(%d of %d) Marking seen rect %d\n", i, this.seenRects.length, j);
            Bounds bounds = (Bounds)seenRectInfo[0];
            double minTilesAcross = (double)seenRectInfo[1];

            quadTreeRoot.markBoundsSeen(bounds, minTilesAcross, this.tileController);
            quadTreeRoot.checkIntegrity();
        }
    }

    protected void checkReferenceTiles(QuadTreeNode quadTreeRoot, Integer orderSeed) {
        List<Integer> remapping = IntStream.range(0, this.referenceTiles.length).boxed().collect(Collectors.toList());
        if (orderSeed != null) {
            Collections.shuffle(remapping, new Random((long)orderSeed));
        }

        for (int i = 0; i<this.referenceTiles.length; i++) {
            int j = remapping.get(i);
            Object[] referenceTileInfo = this.referenceTiles[j];
            System.out.format("(%d of %d) Checking reference tile %d\n", i, this.referenceTiles.length, j);
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
            ).getMask(true, true, this.tileController);
            quadTreeRoot.checkIntegrity();

//             System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(
//                 ((DataBufferByte) result.getData().getDataBuffer()).getData())
//             );

            assertArrayEquals(
                ((DataBufferByte) result.getData().getDataBuffer()).getData(),
                maskBytes
            );
        }
    }
}
