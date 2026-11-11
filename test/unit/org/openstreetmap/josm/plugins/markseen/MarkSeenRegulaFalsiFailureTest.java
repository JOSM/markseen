// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class MarkSeenRegulaFalsiFailureTest {
    @Test
    public void testExceededIterations() throws Exception {
        assertThrows(MarkSeenRegulaFalsi.ExceededIterationsException.class, () ->
            MarkSeenRegulaFalsi.regulaFalsi(
                x -> Math.cos(x) + (0.1 * x * x) - 2.,
                0,
                10,
                0.0001,
                4
            ));
    }

    @Test
    public void testDoublePositive() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
            MarkSeenRegulaFalsi.regulaFalsi(
                x -> Math.sin(x),
                3.2,
                3.3,
                0.001,
                8
            ));
    }

    @Test
    public void testDoubleNegative() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
            MarkSeenRegulaFalsi.regulaFalsi(
                x -> Math.sin(x),
                2.9,
                3.0,
                0.001,
                8
            ));
    }

    @Test
    public void testGeometricSearchExceedRange() throws Exception {
        assertThrows(MarkSeenRegulaFalsi.ExceededRangeDetermination.class, () ->
            // searching upwards, this will never change sign
            MarkSeenRegulaFalsi.regulaFalsiGeometricSearch(
                x -> x * x,
                9.9,
                2.,
                0.001,
                8
            ));
    }
}
