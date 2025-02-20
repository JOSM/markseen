// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class QuadTreeNodeClearOrderTest extends BaseQuadTreeNodeTest {
    private static final int seenRectVariants = 8;
    private static final int referenceTileVariants = 2;

    @Parameters(name = "{index}-scenario-{0}-seeds-{1}-{2}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i = 0; i < scenarios.length; i++) {
            Object[] seenRects = (Object[]) scenarios[i][1];
            Object[] referenceTiles = (Object[]) scenarios[i][2];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for (int m = 1; m <= seenRects.length && srFact <= seenRectVariants; m++) {
                srFact = srFact*m;
            }

            // we'd rather avoid testing against more permutations than exist for the number of referenceTiles
            int rtFact = 1;
            for (int m = 1; m <= referenceTiles.length && rtFact <= referenceTileVariants; m++) {
                rtFact = rtFact*m;
            }

            for (int j = 0; j < Math.min(srFact, seenRectVariants); j++) {
                for (int k = 0; k < Math.min(rtFact, referenceTileVariants); k++) {
                    paramSets.add(new Object[] {i, j, k});
                }
            }
        }
        return paramSets;
    }

    public QuadTreeNodeClearOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @Test
    public void testClearPreSeen() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, null, false);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, true, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }

    @Test
    public void testClearUnseen() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, true, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }
}
