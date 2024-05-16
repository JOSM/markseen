package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


final class QuadTreeMetaSeenRectOrderTest extends BaseQuadTreeMetaTest {
    private static final int variants = 16;

    static Collection<Object[]> getParameters() throws IOException {
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

    @ParameterizedTest(name="{index}-scenario-{0}-seed-{1}")
    @MethodSource("getParameters")
    void test(int scenarioIndex, Integer seenRectOrderSeed, Integer referenceTileOrderSeed) throws IOException {
        super.setup(scenarioIndex, seenRectOrderSeed, referenceTileOrderSeed);
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(this.quadTreeMeta, this.referenceTiles);

        this.markRectsAsync(this.quadTreeMeta, this.seenRects, this.seenRectOrderSeed);

        // wait until the edits have properly started
        while (this.quadTreeMeta.getEditRequestQueueCompletedTaskCount() == 0);

        this.inspectReferenceTiles(this.quadTreeMeta, dynamicReferences, this.referenceTiles, this.referenceTileOrderSeed);
    }
}
