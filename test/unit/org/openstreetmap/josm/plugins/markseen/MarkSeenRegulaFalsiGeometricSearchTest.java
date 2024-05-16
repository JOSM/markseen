package org.openstreetmap.josm.plugins.markseen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoubleUnaryOperator;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


final class MarkSeenRegulaFalsiGeometricSearchTest {
    static Collection<Object[]> getParameters() throws Exception {
        final Object[][] unexpanded = new Object[][] {
            {
                (DoubleUnaryOperator)(x -> Math.cos(x) + (0.1*x*x) - 2.),
                0.01,
                10,
                new double[] {
                    0.005,
                    0.04,
                    1,
                    1.1,
                    4.5
                },
                2.
            },
            {
                (DoubleUnaryOperator)(x -> 1./x - 0.5),
                0.001,
                6,
                new double[] {
                    17.6e-7,
                    0.0055,
                    0.03,
                    0.6,
                    1.1
                },
                5.
            },
            {
                (DoubleUnaryOperator)(x -> Math.sqrt(x)-105.),
                0.0001,
                12,
                new double[] {
                    0.001,
                    1.1,
                    12,
                    1001
                },
                1.8
            },
            {
                (DoubleUnaryOperator)(x -> 10./x - 50.),
                0.001,
                10,
                new double[] {
                    12.,
                    50.,
                    100.,
                    785.3,
                    1616.
                },
                0.5
            },
            {
                ((DoubleUnaryOperator)(x -> (x*x - 30.) * 20.)).compose(x -> x-30.),
                0.001,
                6,
                new double[] {
                    35.,
                    30.,
                    25.
                },
                0.8
            }
        };

        ArrayList<Object[]> paramSets = new ArrayList<>();
        for (int i=0; i<unexpanded.length; i++) {
            double[] initialXs = (double[])unexpanded[i][3];
            DoubleUnaryOperator fx = (DoubleUnaryOperator)unexpanded[i][0];

            for (int m=0; m<2; m++) {
                // possibly negate the output from fx (the "y value")
                DoubleUnaryOperator fxm = m == 0 ? fx : fx.andThen(y -> -y);
                String ySign = m == 0 ? "ypos" : "yneg";

                for (double x : initialXs) {
                    paramSets.add(new Object[] {
                        i,
                        ySign,
                        fxm,
                        unexpanded[i][1],
                        unexpanded[i][2],
                        x,
                        unexpanded[i][4]
                    });
                }
            }
        }
        return paramSets;
    }

    @ParameterizedTest(name="{index}-fx-{0}-{1}-lbx-{5}")
    @MethodSource("getParameters")
    void testPositive(
            final int fxIndex,  // fxIndex only present to help in naming scheme
            final String ySign,  // ySign only present to help in naming scheme
            final DoubleUnaryOperator fx,
            final double precision,
            final int maxIterations,
            final double initialX,
            final double searchFactor
    ) throws Exception {
        double result = MarkSeenRegulaFalsi.regulaFalsiGeometricSearch(
            fx,
            initialX,
            searchFactor,
            precision,
            maxIterations
        );

        assertTrue(fx.applyAsDouble(result) < Math.abs(precision));
    }
}
