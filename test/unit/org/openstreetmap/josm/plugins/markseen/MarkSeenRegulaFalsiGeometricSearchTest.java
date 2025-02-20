// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoubleUnaryOperator;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MarkSeenRegulaFalsiGeometricSearchTest {
    @Parameters(name = "{index}-fx-{0}-{1}-lbx-{5}")
    public static Collection<Object[]> getParameters() throws Exception {
        final Object[][] unexpanded = new Object[][] {
            {
                (DoubleUnaryOperator) (x -> Math.cos(x) + (0.1*x*x) - 2.),
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
                (DoubleUnaryOperator) (x -> 1./x - 0.5),
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
                (DoubleUnaryOperator) (x -> Math.sqrt(x)-105.),
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
                (DoubleUnaryOperator) (x -> 10./x - 50.),
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
                ((DoubleUnaryOperator) (x -> (x*x - 30.) * 20.)).compose(x -> x-30.),
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

        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        for (int i = 0; i < unexpanded.length; i++) {
            double[] initialXs = (double[]) unexpanded[i][3];
            DoubleUnaryOperator fx = (DoubleUnaryOperator) unexpanded[i][0];

            for (int m = 0; m < 2; m++) {
                // possibly negate the output from fx (the "y value")
                DoubleUnaryOperator fxm = m == 0 ? fx : fx.andThen(y -> -y);
                String ySign = m == 0 ? "ypos" : "yneg";

                for (int j = 0; j < initialXs.length; j++) {
                    paramSets.add(new Object[] {
                        i,
                        ySign,
                        fxm,
                        unexpanded[i][1],
                        unexpanded[i][2],
                        initialXs[j],
                        unexpanded[i][4]
                    });
                }
            }
        }
        return paramSets;
    }

    private final DoubleUnaryOperator fx;
    private final double precision;
    private final int maxIterations;
    private final double initialX;
    private final double searchFactor;

    public MarkSeenRegulaFalsiGeometricSearchTest(
        final int fxIndex,  // fxIndex only present to help in naming scheme
        final String ySign,  // ySign only present to help in naming scheme
        final DoubleUnaryOperator fx_,
        final double precision_,
        final int maxIterations_,
        final double initialX_,
        final double searchFactor_
    ) {
        this.fx = fx_;
        this.precision = precision_;
        this.maxIterations = maxIterations_;
        this.initialX = initialX_;
        this.searchFactor = searchFactor_;
    }

    @Test
    public void testPositive() throws Exception {
        double result = MarkSeenRegulaFalsi.regulaFalsiGeometricSearch(
            this.fx,
            this.initialX,
            this.searchFactor,
            this.precision,
            this.maxIterations
        );

        assertTrue(this.fx.applyAsDouble(result) < Math.abs(this.precision));
    }
}
