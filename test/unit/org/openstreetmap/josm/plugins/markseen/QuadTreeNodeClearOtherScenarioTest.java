package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class QuadTreeNodeClearOtherScenarioTest extends BaseQuadTreeNodeTest {
    private static final int preSeenRectVariants = 2;
    private static final int seenRectVariants = 2;
    private static final int referenceTileVariants = 2;

    // additional test parameter
    protected final int preScenarioIndex;
    protected final Integer preSeenRectOrderSeed;

    // additional derived test parameter
    protected final Object[][] preSeenRects;
    protected final Object[][] preReferenceTiles;

    @Parameters(name="{index}-scenarios-{0}-{1}-seeds-{2}-{3}-{4}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for(int m=1; m<=seenRects.length && srFact<=seenRectVariants; m++) {
                srFact = srFact*m;
            }
            for (int j=0; j<scenarios.length; j++) {
                Object[] preSeenRects = (Object[])scenarios[j][1];

                // we'd rather avoid testing against more permutations than exist for the number of preSeenRects
                int psrFact = 1;
                for(int m=1; m<=preSeenRects.length && psrFact<=preSeenRectVariants; m++) {
                    psrFact = psrFact*m;
                }

                for (int k=0; k<Math.min(srFact, seenRectVariants); k++) {
                    for (int l=0; l<Math.min(psrFact, preSeenRectVariants); l++) {
                        for (int a=0; a<referenceTileVariants; a++) {
                            paramSets.add(new Object[] {j, i, l, k, a == 0 ? null : a});
                        }
                    }
                }
            }
        }
        return paramSets;
    }

    public QuadTreeNodeClearOtherScenarioTest(
        int preScenarioIndex_,
        int scenarioIndex_,
        Integer preSeenRectOrderSeed_,
        Integer seenRectOrderSeed_,
        Integer referenceTileOrderSeed_
    ) throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
        this.preScenarioIndex = preScenarioIndex_;
        this.preSeenRectOrderSeed = preSeenRectOrderSeed_;

        Object[] preScenario = getTestScenarios()[this.preScenarioIndex];
        this.preSeenRects = (Object[][])preScenario[1];
        this.preReferenceTiles = (Object[][])preScenario[2];
    }

    @Test
    public void testClearPreRead() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        // marking rects with ExtremeAspectRatioException tolerated as the "other" scenario is meant for
        // a different tileSize
        this.markRects(quadTreeMeta, this.preSeenRects, this.preSeenRectOrderSeed, true);
        // not asserting contents - we just want to cause reads of the tiles
        this.inspectReferenceTiles(quadTreeMeta, this.preReferenceTiles, null, false);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        // only assert contents if referenceTileOrderSeed is null as the reference tiles can be order-sensitive
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, this.referenceTileOrderSeed == null);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }

    @Test
    public void testClearUnread() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        // marking rects with ExtremeAspectRatioException tolerated as the "other" scenario is meant for
        // a different tileSize
        this.markRects(quadTreeMeta, this.preSeenRects, this.preSeenRectOrderSeed, true);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        // only assert contents if referenceTileOrderSeed is null as the reference tiles can be order-sensitive
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, this.referenceTileOrderSeed == null);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }
}
