package org.openstreetmap.josm.plugins.markseen;

import java.util.function.DoubleUnaryOperator;


public class MarkSeenRegulaFalsi {
    private MarkSeenRegulaFalsi() {
        // Hide constructor for utility classes
    }

    public static class ExceededIterationsException extends Exception {};

    protected static double regulaFalsi(
        final DoubleUnaryOperator fx,
        double x0,
        double x1,
        final double precision
    ) throws ExceededIterationsException {
        return regulaFalsi(
            fx,
            x0,
            x1,
            precision,
            8
        );
    }

    protected static double regulaFalsi(
        final DoubleUnaryOperator fx,
        double x0,
        double x1,
        final double precision,
        final int maxIterations
    ) throws ExceededIterationsException {
        double y0 = fx.applyAsDouble(x0);
        double y1 = fx.applyAsDouble(x1);

        double xn, yn;

        if (y0 * y1 > 0) {
            throw new UnsupportedOperationException("f(x0) and f(x1) are both " + (y0 < 0 ? "negative" : "positive"));
        }

        for(int i=0; ; i++) {
            assert y0 * y1 < 0;

            xn = ((x0 * y1) - (x1 * y0)) / (y1 - y0);

            if (i > maxIterations) {
                throw new ExceededIterationsException();
            }

            yn = fx.applyAsDouble(xn);

            if (Math.abs(yn) <= Math.abs(precision)) {
                return xn;
            }

            if (yn * y1 < 0) {
                // propagate our previous result back to x0
                x0 = x1;
                y0 = y1;
            } else {
                // Illinois method
                y0 *= 0.5;
            }

            x1 = xn;
            y1 = yn;
        }
    }
}
