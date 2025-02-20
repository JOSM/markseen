// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoubleUnaryOperator;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MarkSeenRegulaFalsiTest {
    @Parameters(name = "{index}-fx-{0}-{1}-{2}-x0-{6}-x1-{7}")
    public static Collection<Object[]> getParameters() throws Exception {
        final Object[][] unexpanded = new Object[][] {
            {
                (DoubleUnaryOperator) (x -> Math.sin(x)),
                0.0001,
                6,
                new double[] {
                    1.8,
                    2.,
                    3.,
                    3.1
                },
                new double[] {
                    3.15,
                    3.2,
                    3.9,
                    4.1
                }
            },
            {
                (DoubleUnaryOperator) (x -> Math.cos(x) + (0.1*x*x) - 2.),
                0.005,
                10,
                new double[] {
                    -4,
                    0,
                    4,
                    4.5
                },
                new double[] {
                    4.7,
                    5,
                    10,
                    30
                }
            },
            {
                ((DoubleUnaryOperator) (x -> Math.log(x) + 1./x - 1.5)).compose(x -> Math.min(x, 2.7)),
                0.01,
                20,
                new double[] {
                    0.01,
                    0.1,
                    0.4
                },
                new double[] {
                    0.5,
                    2,
                    5
                }
            }
        };

        ArrayList<Object[]> paramSets = new ArrayList<Object[]>();
        for (int i = 0; i < unexpanded.length; i++) {
            double[] x0s = (double[]) unexpanded[i][3];
            double[] x1s = (double[]) unexpanded[i][4];
            DoubleUnaryOperator fx = (DoubleUnaryOperator) unexpanded[i][0];

            for (int m = 0; m < 2; m++) {
                // possibly negate the output from fx (the "y value")
                DoubleUnaryOperator fxm = m == 0 ? fx : fx.andThen(y -> -y);
                String ySign = m == 0 ? "ypos" : "yneg";

                for (int n = 0; n < 2; n++) {
                    // possibly negate the input to fx (the "x value") along with negating x0 and x1
                    DoubleUnaryOperator fxmn = n == 0 ? fxm : fxm.compose(x -> -x);
                    double xSignMul = n == 0 ? 1. : -1.;
                    String xSign = n == 0 ? "xpos" : "xneg";

                    for (int j = 0; j < x0s.length; j++) {
                        for (int k = 0; k < x1s.length; k++) {
                            paramSets.add(new Object[] {
                                i,
                                xSign,
                                ySign,
                                fxmn,
                                unexpanded[i][1],
                                unexpanded[i][2],
                                x0s[j] * xSignMul,
                                x1s[k] * xSignMul
                            });
                            paramSets.add(new Object[] {
                                i,
                                xSign,
                                ySign,
                                fxmn,
                                unexpanded[i][1],
                                unexpanded[i][2],
                                x1s[k] * xSignMul,
                                x0s[j] * xSignMul
                            });
                        }
                    }
                }
            }
        }
        return paramSets;
    }

    private final DoubleUnaryOperator fx;
    private final double precision;
    private final int maxIterations;
    private final double x0;
    private final double x1;

    public MarkSeenRegulaFalsiTest(
        final int fxIndex,  // fxIndex only present to help in naming scheme
        final String xSign,  // xSign only present to help in naming scheme
        final String ySign,  // ySign only present to help in naming scheme
        final DoubleUnaryOperator fx_,
        final double precision_,
        final int maxIterations_,
        final double x0_,
        final double x1_
    ) {
        this.fx = fx_;
        this.precision = precision_;
        this.maxIterations = maxIterations_;
        this.x0 = x0_;
        this.x1 = x1_;
    }

    @Test
    public void testSuccess() throws Exception {
        double result = MarkSeenRegulaFalsi.regulaFalsi(
            this.fx,
            this.x0,
            this.x1,
            this.precision,
            this.maxIterations
        );

        if (this.fx.applyAsDouble(result) > Math.abs(this.precision)) {
            fail("x =" + result + ", y =" + this.fx.applyAsDouble(result) + ", precision=" + this.precision);
        }
    }
}
