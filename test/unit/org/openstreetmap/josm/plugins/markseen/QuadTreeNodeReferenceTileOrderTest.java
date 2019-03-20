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
public class QuadTreeNodeReferenceTileOrderTest extends BaseQuadTreeNodeTest {
    private static final int seenRectVariants = 4;
    private static final int referenceTileVariants = 4;

    @Parameters(name="{index}-scenario-{0}-seeds-{1}-{2}")
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

            // we'd rather avoid testing against more permutations than exist for the number of referenceTiles
            int rtFact = 1;
            for(int m=1; m<=seenRects.length && rtFact<=referenceTileVariants; m++) {
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

    public QuadTreeNodeReferenceTileOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @Test
    public void test() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRects, this.seenRectOrderSeed);
        // because some of the reference tiles are sensitive to the order they were requested in, we can't actually
        // check the contents here - still, we're going to be executing all QuadTreeNode's internal assertions as we do
        // this at least.
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();

        // now, we *should* be able to re-read those same tiles without the ability to write and nothing should
        // complain. provided, of course, java hasn't decided to reclaim any of the SoftReferences, but that's a pretty
        // slim possibility.
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTiles, this.referenceTileOrderSeed, false, null, false);
    }
}
