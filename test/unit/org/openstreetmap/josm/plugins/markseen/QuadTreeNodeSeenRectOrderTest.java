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
public class QuadTreeNodeSeenRectOrderTest extends BaseQuadTreeNodeTest {
    private static final int variants = 16;

    @Parameters(name = "{index}-scenario-{0}-seed-{1}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i = 0; i < scenarios.length; i++) {
            Object[] seenRects = (Object[]) scenarios[i][1];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for (int m = 1; m <= seenRects.length && srFact <= variants; m++) {
                srFact = srFact*m;
            }

            for (int j = 0; j < Math.min(srFact, variants); j++) {
                paramSets.add(new Object[] {i, j});
            }
        }
        return paramSets;
    }

    public QuadTreeNodeSeenRectOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, null);
    }

    @Test
    public void test() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();

        // now, we *should* be able to re-read those same tiles without the ability to write and nothing should
        // complain. provided, of course, java hasn't decided to reclaim any of the SoftReferences, but that's a pretty
        // slim possibility.
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, true, null, false);
    }
}
