package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;

import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.tools.Logging;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class QuadTreeNodeOptimizeTest extends BaseQuadTreeNodeTest {
    private static final int variants = 6;

    // parametrized variables
    protected final Integer optimizeStride;

    @Parameters(name="{index}-scenario-{0}-seed-{1}-stride-{2}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for(int m=1; m<=seenRects.length && srFact<=variants; m++) {
                srFact = srFact*m;
            }

            for (int j=0; j<Math.min(srFact, variants); j++) {
                // variant with null optimizeStride for only optimizing at end
                paramSets.add(new Object[] {i, j, null});

                // use fib sequence to choose the rest of the stride values
                int a = 1, b = 1, c;
                while (b < seenRects.length) {
                    paramSets.add(new Object[] {i, j, b});
                    c = a + b;
                    a = b;
                    b = c;
                }
            }
        }
        return paramSets;
    }

    public QuadTreeNodeOptimizeTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer optimizeStride_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, null);
        this.optimizeStride = optimizeStride_;
    }

    @Test
    public void test() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed, false, (i, j, bounds, minTilesAcross) -> {
            if (this.optimizeStride != null && i % this.optimizeStride == 0) {
                quadTreeMeta.quadTreeRoot.optimize();
            }
        });
        quadTreeMeta.quadTreeRoot.optimize();

        this.inspectReferenceTiles(
            quadTreeMeta,
            this.referenceTiles,
            this.referenceTileOrderSeed,
            false,
            null,
            true,
            (node, i, j, resultMask, refMask, resultMaskBytes, refMaskBytes, aliasable) -> {

                if (aliasable) {
                    assertTrue(
                        "Failed asserting identity " + refMask.toString() + " of ref tile " + j,
                        resultMask == ((boolean) refMask ? quadTreeMeta.FULL_MASK : quadTreeMeta.EMPTY_MASK)
                    );
                } else if (Boolean.class.isInstance(refMask)) {
                    try {
                        if (!(boolean) TestUtils.getPrivateField(node, "belowCanonical")) {
                            Logging.info(
                                "Unaliasable optimized node of refTile " + j
                                + " isn't belowCanonical but still constant value. that's interesting."
                                // this usually happens when differing pixels get squashed out by
                                // the rescaling process at a higher level
                            );
                        }
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                }
            }
        );

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }
}
