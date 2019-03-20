package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;

import java.awt.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class QuadTreeMetaSeenRectOrderTest extends BaseQuadTreeMetaTest {
    private static final int variants = 16;

    @Parameters(name="{index}-scenario-{0}-seed-{1}")
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
                paramSets.add(new Object[] {i, j, null});
            }
        }
        return paramSets;
    }

    public QuadTreeMetaSeenRectOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @Test(timeout=10000)
    public void test() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5);
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(quadTreeMeta, this.referenceTiles);

        this.markRectsAsync(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);

        // wait until the edits have properly started
        while (quadTreeMeta.getEditRequestQueueCompletedTaskCount() == 0);

        this.inspectReferenceTiles(quadTreeMeta, dynamicReferences, this.referenceTiles, this.referenceTileOrderSeed);
    }
}
