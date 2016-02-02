/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.data.summary;

import org.brunel.data.Data;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates a regression function
 */
public class Regression implements Fit {
    private Double m, b;                                            // Slope and intercept

    public Regression(Field y, Field x, List<Integer> rows) {
        double[][] data = asPairs(y, x, rows);
        int n = data[0].length;
        if (n == 0) return;                                      // No data
        double my = mean(data[0]);
        double mx = mean(data[1]);
        double sxy = 0, sxx = 0;                                 // sum of XY and XX values
        for (int i = 0; i < n; i++) {
            Double yv = data[0][i];
            Double xv = data[1][i];
            sxy += (xv - mx) * (yv - my);
            sxx += (xv - mx) * (xv - mx);
        }
        if (sxx > 0) {
            m = sxy / sxx;                                     // We have a slope
            b = my - m * mx;                                   // and intercept
        }
    }

    static double mean(double[] values) {
        double s = 0;
        for (int i = 0; i < values.length; i++) s += values[i];
        return s / values.length;
    }

    /**
     * Creates two arrays [x, y] where all the values are valid and the x's are sorted
     *
     * @param y    y field
     * @param x    x field
     * @param rows the rows to include in this data array
     * @return array of length two, each of which is a field of data
     */
    static double[][] asPairs(Field y, Field x, List<Integer> rows) {
        List<Double> xList = new ArrayList<Double>();
        List<Double> yList = new ArrayList<Double>();
        int n = rows.size();
        for (int k=0; k< n; k++) {
            int i = rows.get(k);
            Double xv = Data.asNumeric(x.value(i));
            Double yv = Data.asNumeric(y.value(i));
            if (xv != null && yv != null) {
                xList.add(xv);
                yList.add(yv);
            }
        }

        Integer[] order = Data.order(xList.toArray(new Double[xList.size()]), true);
        double[] xx = new double[order.length];
        double[] yy = new double[order.length];
        for (int i = 0; i < order.length; i++) {
            xx[i] = xList.get(order[i]);
            yy[i] = yList.get(order[i]);
        }
        return new double[][]{yy, xx};
    }

    public Double get(Object value) {
        return m == null ? null : m * Data.asNumeric(value) + b;
    }
}
