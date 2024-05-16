package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;

import java.awt.Color;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


final class QuadTreeNodeClearOrderTest extends BaseQuadTreeNodeTest {
    private static final int seenRectVariants = 8;
    private static final int referenceTileVariants = 2;

    static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];
            Object[] referenceTiles = (Object[])scenarios[i][2];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for(int m=1; m<=seenRects.length && srFact<=seenRectVariants; m++) {
                srFact = srFact*m;
            }

            // we'd rather avoid testing against more permutations than exist for the number of referenceTiles
            int rtFact = 1;
            for(int m=1; m<=referenceTiles.length && rtFact<=referenceTileVariants; m++) {
                rtFact = rtFact*m;
            }

            for (int j=0; j<Math.min(srFact, seenRectVariants); j++) {
                for (int k=0; k<Math.min(rtFact, referenceTileVariants); k++) {
                    paramSets.add(new Object[] {i, j, k});
                }
            }
        }
        return paramSets;
    }

    @ParameterizedTest(name="{index}-scenario-{0}-seeds-{1}-{2}")
    @MethodSource("getParameters")
    void testClearPreSeen(int scenarioIndex, Integer seenRectOrderSeed, Integer referenceTileOrderSeed) throws IOException {
        super.setup(scenarioIndex, seenRectOrderSeed, referenceTileOrderSeed);
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, null, false);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, true, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }

    @ParameterizedTest(name="{index}-scenario-{0}-seeds-{1}-{2}")
    @MethodSource("getParameters")
    void testClearUnseen(int scenarioIndex, Integer seenRectOrderSeed, Integer referenceTileOrderSeed) throws IOException {
        super.setup(scenarioIndex, seenRectOrderSeed, referenceTileOrderSeed);
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, true, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }
}
