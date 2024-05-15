package org.openstreetmap.josm.plugins.markseen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


final class QuadTreeMetaClearOrderTest extends BaseQuadTreeMetaTest {
    private static final int seenRectVariants = 8;
    private static final int referenceTileVariants = 2;

    static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
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
    void testClearUnseen(int scenarioIndex, Integer seenRectOrderSeed, Integer referenceTileOrderSeed)
            throws InterruptedException, ExecutionException, IOException {
        setup(scenarioIndex, seenRectOrderSeed, referenceTileOrderSeed);
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(this.quadTreeMeta, this.referenceTiles);

        this.markRectsAsync(this.quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        boolean clearSuccess = false;
        while (!clearSuccess) {
            try {
                this.quadTreeMeta.requestClear(true);
                clearSuccess = true;
            } catch (RejectedExecutionException e) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e2) {}
                // then retry
            }
        }

        // wait until the edits have properly started
        while (this.quadTreeMeta.getEditRequestQueueCompletedTaskCount() == 0);

        final ExecutorService executor = Executors.newFixedThreadPool(4);
        final List<Future<Object>> maskFutures = this.fetchTileMasksAsync(
            this.quadTreeMeta,
            dynamicReferences,
            executor,
            this.referenceTileOrderSeed
        );

        byte[] blankMaskBytes = getRefMaskBytes(this.quadTreeMeta, false);
        for (int i = 0; i < maskFutures.size(); i++) {
            System.out.format("(%d of %d) Checking reference tile %d\n", i, this.referenceTiles.length, i);
            byte[] resultMaskBytes = getRefMaskBytes(this.quadTreeMeta, maskFutures.get(i).get());
            assertArrayEquals(
                resultMaskBytes,
                blankMaskBytes,
                "assertArrayEquals failed on reference tile " + i
            );
        }
    }
}
