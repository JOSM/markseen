package org.openstreetmap.josm.plugins.markseen;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import java.awt.Color;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.data.Bounds;


final class QuadTreeNodeExtremeAspectRatioTest extends BaseTest {
    static Collection<Object[]> getParameters() throws IOException {
        return Arrays.asList(scenarios);
    }

    private final static Object[][] scenarios = new Object[][] {
        { 256, new Bounds(51.36, -0.35, 51.3601, 0.10), 1.1 },
        { 128, new Bounds(20.22, -35.2, 20.23, -35.1999999), 4.2 },
        { 161, new Bounds(-0.0001, -1.0, 0.0001, 1.0), 8.3 },
        { 161, new Bounds(-1.0001, -1.0, -0.9999, 1.0), 8.3 }
    };

    @ParameterizedTest
    @MethodSource("getParameters")
    void test(int tileSize, Bounds bounds, double minTilesAcross) {
        QuadTreeMeta quadTreeMeta = new QuadTreeMeta(tileSize, Color.PINK, 0.5, false);
        quadTreeMeta.quadTreeRWLock.writeLock().lock();

        try {
            assertThrows(QuadTreeNode.ExtremeAspectRatioException.class, () -> quadTreeMeta.quadTreeRoot.markBoundsSeen(bounds, minTilesAcross));
        } finally {
            quadTreeMeta.quadTreeRWLock.writeLock().unlock();
        }
    }
}
