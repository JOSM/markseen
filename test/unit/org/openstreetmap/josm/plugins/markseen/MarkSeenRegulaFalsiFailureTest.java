package org.openstreetmap.josm.plugins.markseen;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;


final class MarkSeenRegulaFalsiFailureTest {
    @Test
    void testExceededIterations() {
        assertThrows(MarkSeenRegulaFalsi.ExceededIterationsException.class, () -> MarkSeenRegulaFalsi.regulaFalsi(
            x -> Math.cos(x) + (0.1*x*x) - 2.,
            0,
            10,
            0.0001,
            4
        ));
    }

    @Test
    void testDoublePositive() {
        assertThrows(UnsupportedOperationException.class, () -> MarkSeenRegulaFalsi.regulaFalsi(
             Math::sin,
            3.2,
            3.3,
            0.001,
            8
        ));
    }

    @Test
    void testDoubleNegative() {
        assertThrows(UnsupportedOperationException.class, () -> MarkSeenRegulaFalsi.regulaFalsi(
             Math::sin,
            2.9,
            3.0,
            0.001,
            8
        ));
    }

    @Test
    void testGeometricSearchExceedRange() {
        // searching upwards, this will never change sign
        assertThrows(MarkSeenRegulaFalsi.ExceededRangeDetermination.class, () -> MarkSeenRegulaFalsi.regulaFalsiGeometricSearch(
            x -> x*x,
            9.9,
            2.,
            0.001,
            8
        ));
    }
}
