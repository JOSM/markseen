package org.openstreetmap.josm.plugins.markseen;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


final class MarkSeenRegulaFalsiTest {
    static Collection<Object[]> getParameters() throws Exception {
        final Object[][] unexpanded = new Object[][] {
            {
                (DoubleUnaryOperator)(Math::sin),
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
                (DoubleUnaryOperator)(x -> Math.cos(x) + (0.1*x*x) - 2.),
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
                ((DoubleUnaryOperator)(x -> Math.log(x) + 1./x - 1.5)).compose(x -> Math.min(x, 2.7)),
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

        ArrayList<Object[]> paramSets = new ArrayList<>();
        for (int i=0; i<unexpanded.length; i++) {
            double[] x0s = (double[])unexpanded[i][3];
            double[] x1s = (double[])unexpanded[i][4];
            DoubleUnaryOperator fx = (DoubleUnaryOperator)unexpanded[i][0];

            for (int m=0; m<2; m++) {
                // possibly negate the output from fx (the "y value")
                DoubleUnaryOperator fxm = m == 0 ? fx : fx.andThen(y -> -y);
                String ySign = m == 0 ? "ypos" : "yneg";

                for (int n=0; n<2; n++) {
                    // possibly negate the input to fx (the "x value") along with negating x0 and x1
                    DoubleUnaryOperator fxmn = n == 0 ? fxm : fxm.compose(x -> -x);
                    double xSignMul = n == 0 ? 1. : -1.;
                    String xSign = n == 0 ? "xpos" : "xneg";

                    for (int j=0; j<x0s.length; j++) {
                        for (int k=0; k<x1s.length; k++) {
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

    @ParameterizedTest(name="{index}-fx-{0}-{1}-{2}-x0-{6}-x1-{7}")
    @MethodSource("getParameters")
    void testSuccess(
            final int fxIndex,  // fxIndex only present to help in naming scheme
            final String xSign,  // xSign only present to help in naming scheme
            final String ySign,  // ySign only present to help in naming scheme
            final DoubleUnaryOperator fx,
            final double precision,
            final int maxIterations,
            final double x0,
            final double x1
    ) throws Exception {
        double result = MarkSeenRegulaFalsi.regulaFalsi(
            fx,
            x0,
            x1,
            precision,
            maxIterations
        );

        if (fx.applyAsDouble(result) > Math.abs(precision)) {
            fail("x =" + result + ", y =" + fx.applyAsDouble(result) + ", precision=" + precision);
        }
    }
}
