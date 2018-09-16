package org.openstreetmap.josm.plugins.markseen;

import org.junit.Test;


public class MarkSeenRegulaFalsiFailureTest {
    @Test(expected=MarkSeenRegulaFalsi.ExceededIterationsException.class)
    public void testExceededIterations() throws Exception {
        MarkSeenRegulaFalsi.regulaFalsi(
            x -> Math.cos(x) + (0.1*x*x) - 2.,
            0,
            10,
            0.0001,
            4
        );
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testDoublePositive() throws Exception {
        MarkSeenRegulaFalsi.regulaFalsi(
            x -> Math.sin(x),
            3.2,
            3.3,
            0.001,
            8
        );
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testDoubleNegative() throws Exception {
        MarkSeenRegulaFalsi.regulaFalsi(
            x -> Math.sin(x),
            2.9,
            3.0,
            0.001,
            8
        );
    }
}
