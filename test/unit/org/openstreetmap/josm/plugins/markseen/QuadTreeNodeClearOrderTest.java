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
public class QuadTreeNodeClearOrderTest extends BaseQuadTreeNodeTest {
    private static final int seenRectVariants = 8;
    private static final int referenceTileVariants = 2;

    @Parameters(name="{index}-scenario-{0}-seeds-{1}-{2}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];

            // we'd rather avoid testing against more permutations than exist for the number of seenRects
            int srFact = 1;
            for(int m=1; m<=seenRects.length; m++) {
                srFact = srFact*m;
            }

            for (int j=0; j<Math.min(srFact, seenRectVariants); j++) {
                for (int k=0; k<referenceTileVariants; k++) {
                    paramSets.add(new Object[] {i, j, k});
                }
            }
        }
        return paramSets;
    }

    public QuadTreeNodeClearOrderTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @Test
    public void testClearPreSeen() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRectOrderSeed);
        this.inspectReferenceTiles(quadTreeMeta, null, false);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTileOrderSeed, true, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }

    @Test
    public void testClearUnseen() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        this.markRects(quadTreeMeta, this.seenRectOrderSeed);
        quadTreeMeta.quadTreeRoot.clear();
        quadTreeMeta.quadTreeRoot.checkIntegrity();
        this.inspectReferenceTiles(quadTreeMeta, this.referenceTileOrderSeed, true, false);

        quadTreeMeta.quadTreeRWLock.writeLock().unlock();
    }
}
