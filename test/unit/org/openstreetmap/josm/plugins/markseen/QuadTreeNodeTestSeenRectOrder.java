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
public class QuadTreeNodeTestSeenRectOrder extends BaseQuadTreeNodeTest {
    private static final int variants = 16;

    @Parameters(name="{index}-scenario-{0}-seed-{1}")
    public static Collection<Object[]> getParameters() throws IOException {
        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        Object[][] scenarios = getTestScenarios();
        for (int i=0; i<scenarios.length; i++) {
            Object[] seenRects = (Object[])scenarios[i][1];

            int lenFact = 1;
            for(int k=1; k<=scenarios.length; k++) {
                lenFact = lenFact*k;
            }

            for (int j=0; j<Math.min(lenFact, variants); j++) {
                paramSets.add(new Object[] {i, j, null});
            }
        }
        return paramSets;
    }

    public QuadTreeNodeTestSeenRectOrder(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @Test
    public void test() {
        QuadTreeNode quadTreeRoot = new QuadTreeNode(this.tileController);
        this.lock.writeLock().lock();

        this.markRects(quadTreeRoot, this.seenRectOrderSeed);
        this.checkReferenceTiles(quadTreeRoot, this.referenceTileOrderSeed);

        this.lock.writeLock().unlock();
    }
}
