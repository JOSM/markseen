package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.lang.Math;
import java.util.Arrays;
import java.util.Collection;

import java.awt.Color;

import org.openstreetmap.josm.data.Bounds;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class QuadTreeNodeExtremeAspectRatioTest extends BaseTest {
    @Parameters()
    public static Collection<Object[]> getParameters() throws IOException {
        return Arrays.asList(scenarios);
    }

    protected final static Object[][] scenarios = new Object[][] {
        { 256, new Bounds(51.36, -0.35, 51.3601, 0.10), 1.1 },
        { 128, new Bounds(20.22, -35.2, 20.23, -35.1999999), 4.2 },
        { 161, new Bounds(-0.0001, -1.0, 0.0001, 1.0), 8.3 },
        { 161, new Bounds(-1.0001, -1.0, -0.9999, 1.0), 8.3 }
    };

    protected final int tileSize;
    protected final Bounds bounds;
    protected final double minTilesAcross;

    public QuadTreeNodeExtremeAspectRatioTest(int tileSize_, Bounds bounds_, double minTilesAcross_) {
        this.tileSize = tileSize_;
        this.bounds = bounds_;
        this.minTilesAcross = minTilesAcross_;
    }

    @Test(expected=QuadTreeNode.ExtremeAspectRatioException.class)
    public void test() {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(this.tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        try {
            quadTreeMeta.quadTreeRoot.markBoundsSeen(this.bounds, this.minTilesAcross);
        } finally {
            quadTreeMeta.quadTreeRWLock.writeLock().unlock();
        }
    }
}
