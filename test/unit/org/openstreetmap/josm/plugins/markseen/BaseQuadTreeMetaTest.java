package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.AssertionError;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;

import java.awt.image.DataBufferByte;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Ignore;
import org.mockito.Mockito;


@Ignore
public class BaseQuadTreeMetaTest extends BaseRectTest {
    public static QuadTreeNodeDynamicReference[] createDynamicReferences(QuadTreeMeta quadTreeMeta, Object[][] referenceTiles_) {
        QuadTreeNodeDynamicReference[] refs = new QuadTreeNodeDynamicReference[referenceTiles_.length];
        for (int i=0; i<referenceTiles_.length; i++) {
            Tile mockTile = Mockito.mock(Tile.class);
            Mockito.when(mockTile.getZoom()).thenReturn((int)referenceTiles_[i][0]);
            Mockito.when(mockTile.getXtile()).thenReturn((int)referenceTiles_[i][1]);
            Mockito.when(mockTile.getYtile()).thenReturn((int)referenceTiles_[i][2]);

            refs[i] = new QuadTreeNodeDynamicReference(quadTreeMeta, mockTile);
        }
        return refs;
    }

    public BaseQuadTreeMetaTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    protected void markRectsAsync(QuadTreeMeta quadTreeMeta, Object[][] seenRects_, Integer orderSeed) {
        List<Integer> remapping = getRemapping(seenRects_.length, orderSeed);

        for (int i = 0; i<seenRects_.length; i++) {
            int j = remapping.get(i);
            Object[] seenRectInfo = seenRects_[j];
            System.out.format("(%d of %d) Requesting seen rect mark %d\n", i, seenRects_.length, j);
            Bounds bounds = (Bounds)seenRectInfo[0];
            double minTilesAcross = (double)seenRectInfo[1];

            quadTreeMeta.requestSeenBoundsMark(bounds, minTilesAcross, true);
        }
    }

    protected void inspectReferenceTiles(QuadTreeMeta quadTreeMeta, QuadTreeNodeDynamicReference[] dynamicReferences, Object [][] referenceTiles_, Integer orderSeed) {
        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, referenceTiles_, orderSeed, true);
    }

    protected void inspectReferenceTiles(QuadTreeMeta quadTreeMeta, QuadTreeNodeDynamicReference[] dynamicReferences, Object [][] referenceTiles_, Integer orderSeed, boolean assertContents) {
        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, referenceTiles_, orderSeed, assertContents, null);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        QuadTreeNodeDynamicReference[] dynamicReferences,
        Object [][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask
    ) {
        assert dynamicReferences.length == referenceTiles_.length;

        List<Integer> remapping = getRemapping(referenceTiles_.length, orderSeed);

        for (int i = 0; i<referenceTiles_.length; i++) {
            final int j = remapping.get(i);
            Object[] referenceTileInfo = referenceTiles_[j];
            System.out.format("(%d of %d) Checking reference tile %d\n", i, referenceTiles_.length, j);
            byte[] refMaskBytes = getRefMaskBytes(quadTreeMeta, constReferenceMask != null ? constReferenceMask : referenceTileInfo[3]);

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
                    System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(resultMaskBytes));
                    throw e;
                }
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
        for (int i = 0; i<dynamicReferences.length; i++) {
            maskFutures.add(null);
        }

        for (int i = 0; i<dynamicReferences.length; i++) {
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
