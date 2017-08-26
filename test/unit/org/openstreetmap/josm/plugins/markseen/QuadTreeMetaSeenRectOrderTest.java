package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.lang.AssertionError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;


@RunWith(Parameterized.class)
public class QuadTreeMetaSeenRectOrderTest extends BaseRectTest {
    private static final int variants = 16;

    @Parameters(name="{index}-scenario-{0}-seed-{1}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for(int m=1; m<=seenRects.length; m++) {
                srFact = srFact*m;
            }

            for (int j=0; j<Math.min(srFact, variants); j++) {
                paramSets.add(new Object[] {i, j, null});
            }
        }
        return paramSets;
    }

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

    public QuadTreeMetaSeenRectOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
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
            int j = remapping.get(i);
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

    @Test(timeout=10000)
    public void test() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5);
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(quadTreeMeta, this.referenceTiles);

        this.markRectsAsync(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);

        // wait until the edits have started (or have finished)
        while (!(quadTreeMeta.quadTreeRWLock.isWriteLocked() || quadTreeMeta.isEditRequestQueueEmpty()));

        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, this.referenceTiles, this.referenceTileOrderSeed);
    }
}
