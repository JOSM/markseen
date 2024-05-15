package org.openstreetmap.josm.plugins.markseen;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.Math;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadPoolExecutor;

import java.awt.Color;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;


@Main
@BasicPreferences
@Timeout(20)
final class QuadTreeMetaAutoOptimizeTest extends BaseQuadTreeMetaTest {
    private static final int variants = 2;

    static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<>();
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

    @Override
    protected void initQuadTreeMeta() {
        this.quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, true);
    }

    @ParameterizedTest(name = "{index}-scenario-{0}-batchSize-{1}-pause-{2}ms")
    @MethodSource("getParameters")
    void test(int scenarioIndex, int batchSize, int pauseMS) throws Exception {
        super.setup(scenarioIndex, null, null);
        Config.getPref().putInt("markseen.autoOptimizeDelayMS", 1000);
        QuadTreeNodeDynamicReference[] dynamicReferences = createDynamicReferences(this.quadTreeMeta, this.referenceTiles);

        for (int i=0; i<this.seenRects.length; i+=batchSize) {
            this.markRectsAsync(
                this.quadTreeMeta,
                Arrays.copyOfRange(this.seenRects, i, Math.min(i+batchSize, this.seenRects.length)), this.seenRectOrderSeed
            );
            Thread.sleep(pauseMS);
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
                    assertTrue((boolean) dynamicReference.maskReadOperation(
                        mask -> mask == ((boolean) refMask ? this.quadTreeMeta.FULL_MASK : this.quadTreeMeta.EMPTY_MASK)
                    ), "Failed asserting identity " + refMask.toString() + " of ref tile " + j);
                }
            }
        );
    }
}
