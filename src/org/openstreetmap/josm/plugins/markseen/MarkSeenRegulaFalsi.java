package org.openstreetmap.josm.plugins.markseen;

import java.util.function.DoubleUnaryOperator;


public class MarkSeenRegulaFalsi {
    private MarkSeenRegulaFalsi() {
        // Hide constructor for utility classes
    }

    public static class RegulaFalsiException extends Exception {};

    public static class ExceededIterationsException extends RegulaFalsiException {};

    public static class ExceededRangeDetermination extends RegulaFalsiException {};

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

    protected static double regulaFalsiLowerBound(
        final DoubleUnaryOperator fx,
        double lowerBoundX,
        final double precision,
        final int maxIterations
    ) throws RegulaFalsiException {
        if (lowerBoundX <= 0.) {
            throw new UnsupportedOperationException("regulaFalsiLowerBound only works for a positive lowerBoundX");
        }

        double lowerBoundY = fx.applyAsDouble(lowerBoundX);
        double upperBoundX = -1.;

        for (int i=0; i<32; i++) {
            double newBoundX = lowerBoundX * 2;
            double newBoundY = fx.applyAsDouble(newBoundX);

            if (lowerBoundY * newBoundY > 0) {
                lowerBoundX = newBoundX;
                lowerBoundY = newBoundY;
            } else {
                upperBoundX = newBoundX;
                break;
            }
        }
        if (upperBoundX <= 0.) {
            throw new ExceededRangeDetermination();
        }
        System.out.println("calling regulaFalsi with x0=" + lowerBoundX + ", x1=" + upperBoundX);

        return regulaFalsi(
            fx,
            lowerBoundX,
            upperBoundX,
            precision,
            maxIterations
        );
    };
}
