package org.openstreetmap.josm.plugins.markseen;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.lang.Math;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadPoolExecutor;

import java.awt.Color;

import static org.junit.Assert.assertTrue;

import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;


@RunWith(Parameterized.class)
public class QuadTreeMetaAutoOptimizeTest extends BaseQuadTreeMetaTest {
    private static final int variants = 2;

    // additional test parameters
    protected final int batchSize;
    protected final int pauseMS;

    @Parameters(name="{index}-scenario-{0}-batchSize-{1}-pause-{2}ms")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];

            // use fib sequence to choose batchSize values
            int a = 1, b = 1, c;
            while (b < seenRects.length) {
                paramSets.add(new Object[] {i, b, 150});
                paramSets.add(new Object[] {i, b, 500});
                c = a + b;
                a = b;
                b = c;
            }
        }
        return paramSets;
    }

    @Rule public JOSMTestRules test = new JOSMTestRules().main().preferences().timeout(20000);

    protected void initQuadTreeMeta() {
        this.quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, true);
    }

    public QuadTreeMetaAutoOptimizeTest(int scenarioIndex_, int batchSize_, int pauseMS_)
    throws IOException {
        super(scenarioIndex_, null, null);
        this.batchSize = batchSize_;
        this.pauseMS = pauseMS_;
    }

    @Test
    public void test() throws Exception {
        Config.getPref().putInt("markseen.autoOptimizeDelayMS", 1000);
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(this.quadTreeMeta, this.referenceTiles);

        for (int i=0; i<this.seenRects.length; i+=this.batchSize) {
            this.markRectsAsync(
                this.quadTreeMeta,
                Arrays.copyOfRange(this.seenRects, i, Math.min(i+this.batchSize, this.seenRects.length)), this.seenRectOrderSeed
            );
            Thread.sleep(this.pauseMS);
        }

        Awaitility.await().atMost(30000, MILLISECONDS).until(
            () -> this.quadTreeMeta.getEditRequestQueueCompletedTaskCount() >= this.seenRects.length
        );
        Awaitility.await().atMost(5000, MILLISECONDS).until(
            () -> {
                try {
                    return ((ThreadPoolExecutor) TestUtils.getPrivateField(
                        this.quadTreeMeta,
                        "quadTreeOptimizeExecutor"
                    )).getActiveCount() == 0;
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            }
        );

        this.inspectReferenceTiles(
            this.quadTreeMeta,
            dynamicReferences,
            this.referenceTiles,
            this.referenceTileOrderSeed,
            true,
            null,
            (i, j, dynamicReference, refMask, resultMaskBytes, refMaskBytes, aliasable) -> {
                if (aliasable) {
                    assertTrue(
                        "Failed asserting identity " + refMask.toString() + " of ref tile " + j,
                        dynamicReference.maskReadOperation(
                            mask -> mask == ((boolean) refMask ? this.quadTreeMeta.FULL_MASK : this.quadTreeMeta.EMPTY_MASK)
                        )
                    );
                }
            }
        );
    }
}
