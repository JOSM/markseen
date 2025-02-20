// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class QuadTreeMetaReferenceTileOrderTest extends BaseQuadTreeMetaTest {
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

    public QuadTreeMetaReferenceTileOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @Test(timeout = 10000)
    public void test()
    throws java.lang.InterruptedException, java.util.concurrent.ExecutionException {
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(this.quadTreeMeta, this.referenceTiles);

        this.markRectsAsync(this.quadTreeMeta, this.seenRects, this.seenRectOrderSeed);

        // wait until the edits have properly started
        while (this.quadTreeMeta.getEditRequestQueueCompletedTaskCount() == 0);

        final ExecutorService executor = Executors.newFixedThreadPool(4);
        final List<Future<Object>> maskFutures = this.fetchTileMasksAsync(
            this.quadTreeMeta,
            dynamicReferences,
            executor,
            this.referenceTileOrderSeed
        );

        // because some of the reference tiles are sensitive to the order they were requested in, we can't actually
        // check the contents here - still, we're going to be executing all QuadTreeNode's internal assertions as we do
        // this at least.

        // still we're taking note of the mask results so we can compare them to a second execution
        byte[][] originalMasks = new byte[maskFutures.size()][0];
        for (int i = 0; i < maskFutures.size(); i++) {
            originalMasks[i] = getRefMaskBytes(this.quadTreeMeta, maskFutures.get(i).get());
        }

        // now we *should* be able to re-fetch the same tiles with the read-lock held by this process (thus ensuring
        // that pure reads of masks can be achieved in parallel). provided, of course, java hasn't decided to reclaim
        // any of the SoftReferences, but that's a pretty slim possibility.
        this.quadTreeMeta.quadTreeRWLock.readLock().lock();
        final List<Future<Object>> maskFutures2 = this.fetchTileMasksAsync(
            this.quadTreeMeta,
            dynamicReferences,
            executor,
            this.referenceTileOrderSeed
        );

        for (int i = 0; i < maskFutures2.size(); i++) {
            System.out.format("(%d of %d) Cross-checking reference tile %d\n", i, this.referenceTiles.length, i);
            byte[] secondMask = getRefMaskBytes(this.quadTreeMeta, maskFutures2.get(i).get());
            try {
                assertArrayEquals(
                    originalMasks[i],
                    secondMask
                );
            } catch (final AssertionError e) {
                System.out.format("assertArrayEquals failed on reference tile %d\n", i);
                System.out.println("First read:");
                System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(originalMasks[i]));
                System.out.println("Second read:");
                System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(secondMask));
                throw e;
            }
        }
        this.quadTreeMeta.quadTreeRWLock.readLock().unlock();
    }
}
