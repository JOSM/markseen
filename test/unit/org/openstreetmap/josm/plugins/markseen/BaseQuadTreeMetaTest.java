// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.awt.Color;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Ignore;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.ReflectionUtils;

import mockit.Mock;
import mockit.MockUp;
import mockit.Invocation;

@Ignore
public class BaseQuadTreeMetaTest extends BaseRectTest {
    public static QuadTreeNodeDynamicReference[] createDynamicReferences(QuadTreeMeta quadTreeMeta, Object[][] referenceTiles_) {
        new MockUp<Tile>() {
            @Mock void $init(Invocation invocation, TileSource source, int xtile, int ytile, int zoom) {
                Tile tile = invocation.getInvokedInstance();
                try {
                    final Field xtileField = Tile.class.getDeclaredField("xtile");
                    final Field ytileField = Tile.class.getDeclaredField("ytile");
                    final Field zoomField = Tile.class.getDeclaredField("zoom");
                    ReflectionUtils.setObjectsAccessible(xtileField, ytileField, zoomField);
                    xtileField.setInt(tile, xtile);
                    ytileField.setInt(tile, ytile);
                    zoomField.setInt(tile, zoom);
                } catch (ReflectiveOperationException roe) {
                    throw new JosmRuntimeException(roe);
                }
            }
        };
        QuadTreeNodeDynamicReference[] refs = new QuadTreeNodeDynamicReference[referenceTiles_.length];
        for (int i = 0; i < referenceTiles_.length; i++) {
            Tile mockTile = new Tile(null, (int) referenceTiles_[i][1], (int) referenceTiles_[i][2], (int) referenceTiles_[i][0]);
            refs[i] = new QuadTreeNodeDynamicReference(quadTreeMeta, mockTile);
        }
        return refs;
    }

    protected QuadTreeMeta quadTreeMeta;

    public BaseQuadTreeMetaTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
        this.initQuadTreeMeta();
    }

    protected void initQuadTreeMeta() {
        this.quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
    }

    @After
    public void destroyQuadTreeMeta() throws Exception {
        final ThreadPoolExecutor quadTreeEditExecutor = ((ThreadPoolExecutor)
            TestUtils.getPrivateField(quadTreeMeta, "quadTreeEditExecutor"));
        final ThreadPoolExecutor quadTreeOptimizeExecutor = ((ThreadPoolExecutor)
            TestUtils.getPrivateField(quadTreeMeta, "quadTreeOptimizeExecutor"));

        quadTreeEditExecutor.shutdownNow();
        quadTreeOptimizeExecutor.shutdownNow();
        quadTreeEditExecutor.awaitTermination(20000, MILLISECONDS);
        quadTreeOptimizeExecutor.awaitTermination(20000, MILLISECONDS);
    }

    protected void markRectsAsync(QuadTreeMeta quadTreeMeta, Object[][] seenRects_, Integer orderSeed) {
        List<Integer> remapping = getRemapping(seenRects_.length, orderSeed);

        for (int i = 0; i < seenRects_.length; i++) {
            int j = remapping.get(i);
            Object[] seenRectInfo = seenRects_[j];
            System.out.format("(%d of %d) Requesting seen rect mark %d\n", i, seenRects_.length, j);
            Bounds bounds = (Bounds) seenRectInfo[0];
            double minTilesAcross = (double) seenRectInfo[1];

            boolean success = false;
            while (!success) {
                try {
                    quadTreeMeta.requestSeenBoundsMark(bounds, minTilesAcross, true);
                    success = true;
                } catch (RejectedExecutionException e) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e2) {
                    }
                    // then retry
                }
            }
        }
    }

    @FunctionalInterface
    interface InspectExtraAssertion {
        void assert_(
            int i,
            int j,
            QuadTreeNodeDynamicReference dynamicReference,
            Object refMask,
            byte[] resultMaskBytes,
            byte[] refMaskBytes,
            boolean refMaskOptAliasable
        );
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        QuadTreeNodeDynamicReference[] dynamicReferences,
        Object[][] referenceTiles_,
        Integer orderSeed
    ) {
        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, referenceTiles_, orderSeed, true);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        QuadTreeNodeDynamicReference[] dynamicReferences,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents
    ) {
        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, referenceTiles_, orderSeed, assertContents, null);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        QuadTreeNodeDynamicReference[] dynamicReferences,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask
    ) {
        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, referenceTiles_, orderSeed, assertContents, null, null);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        QuadTreeNodeDynamicReference[] dynamicReferences,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask,
        InspectExtraAssertion extraAssertion
    ) {
        assertTrue("assertContents will not work reliably in non-default order",
            orderSeed == null || constReferenceMask != null || !assertContents);
        assertEquals(dynamicReferences.length, referenceTiles_.length);

        List<Integer> remapping = getRemapping(referenceTiles_.length, orderSeed);

        for (int i = 0; i < referenceTiles_.length; i++) {
            final int j = remapping.get(i);
            Object[] referenceTileInfo = referenceTiles_[j];
            System.out.format("(%d of %d) Checking reference tile %d\n", i, referenceTiles_.length, j);
            Object refMask = constReferenceMask != null ? constReferenceMask : referenceTileInfo[3];
            byte[] refMaskBytes = getRefMaskBytes(quadTreeMeta, refMask);
            boolean refMaskOptAliasable = referenceTileInfo.length >= 5 ? (boolean) referenceTileInfo[4] : false;

            byte[] resultMaskBytes = dynamicReferences[j].maskReadOperation(
                mask -> ((DataBufferByte) mask.getData().getDataBuffer()).getData()
            );

            if (assertContents) {
                try {
                    assertArrayEquals(
                        resultMaskBytes,
                        refMaskBytes
                    );
                } catch (final AssertionError e) {
                    System.out.format("assertArrayEquals failed on reference tile %d\n", j);
                    System.out.println(
                        "ref = " + javax.xml.bind.DatatypeConverter.printHexBinary(refMaskBytes) +
                        ", result = " + javax.xml.bind.DatatypeConverter.printHexBinary(resultMaskBytes)
                    );
                    throw e;
                }
            }
            if (extraAssertion != null) {
                extraAssertion.assert_(
                    i,
                    j,
                    dynamicReferences[j],
                    refMask,
                    resultMaskBytes,
                    refMaskBytes,
                    refMaskOptAliasable
                );
            }
        }
    }

    protected List<Future<Object>> fetchTileMasksAsync(
        final QuadTreeMeta quadTreeMeta,
        final QuadTreeNodeDynamicReference[] dynamicReferences,
        final ExecutorService executor,
        final Integer orderSeed
    ) {
        List<Integer> remapping = getRemapping(dynamicReferences.length, orderSeed);
        List<Future<Object>> maskFutures = new ArrayList<Future<Object>>(dynamicReferences.length);
        for (int i = 0; i < dynamicReferences.length; i++) {
            maskFutures.add(null);
        }

        for (int i = 0; i < dynamicReferences.length; i++) {
            final int j = remapping.get(i);
            System.out.format("(%d of %d) Requesting tile mask %d\n", i, dynamicReferences.length, j);

            maskFutures.set(j, executor.submit(() -> dynamicReferences[j].maskReadOperation(
                mask -> {
                    // returning actual false & true here (cf referenceTile masks) would allow a caller to test for
                    // these special masks
                    if (mask == quadTreeMeta.EMPTY_MASK) {
                        return false;
                    } else if (mask == quadTreeMeta.FULL_MASK) {
                        return true;
                    } else {
                        return ((DataBufferByte) mask.getData().getDataBuffer()).getData();
                    }
                }
            )));
        }

        return maskFutures;
    }
}
