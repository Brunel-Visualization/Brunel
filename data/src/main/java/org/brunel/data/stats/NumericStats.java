/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.data.stats;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.util.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NumericStats {

    public static boolean populate(Field f) {
        int n = f.rowCount();

        // Extract valid numeric data (and count the non-null items)
        int validItemCount = 0;
        List<Double> valid = new ArrayList<Double>();
        for (int i = 0; i < n; i++) {
            Object item = f.value(i);
            if (item != null) {
                validItemCount++;
                if (item instanceof Range) {
                    double low = ((Range) item).low;
                    double high = ((Range) item).high;
                    if (!Double.isInfinite(low)) valid.add(low);
                    if (!Double.isInfinite(high)) valid.add(high);
                } else {
                    Double d = Data.asNumeric(item);
                    if (d != null) valid.add(d);
                }
            }
        }

        f.setProperty("valid", validItemCount);
        Double[] data = valid.toArray(new Double[valid.size()]);
        n = data.length;
        f.setProperty("validNumeric", n);

        // No numeric data -- give up and go home
        if (n == 0) return false;

        // Calculate the moments, used for standard statistics
        double m1 = moment(data, 0, 1, n);
        double m2 = moment(data, m1, 2, n - 1);
        double m3 = moment(data, m1, 3, n - 1);
        double m4 = moment(data, m1, 4, n - 1);
        f.setProperty("mean", m1);
        f.setProperty("stddev", Math.sqrt(m2));
        f.setProperty("variance", m2);
        f.setProperty("skew", m3 / m2 / Math.sqrt(m2));
        f.setProperty("kurtosis", m4 / m2 / m2 - 3.0);

        Arrays.sort(data);
        double min = data[0];
        double max = data[n - 1];
        f.setProperty("min", min);
        f.setProperty("max", max);

        // Order statistics: using the Tukey hinge definition
        f.setProperty("median", av(data, (n - 1) * 0.5));
        if (n % 2 == 0) {
            // Even data, include the median in upper and lower
            f.setProperty("q1", av(data, (n / 2 - 1) * 0.5));
            f.setProperty("q3", av(data, n / 2 + (n / 2 - 1) * 0.5));
        } else {
            // Odd data, do not include the median in upper and lower
            f.setProperty("q1", av(data, (n - 1) * 0.25));
            f.setProperty("q3", av(data, (n - 1) / 2 + (n - 1) * 0.25));
        }

        double minD = max - min;
        if (minD == 0) minD = Math.abs(max);
        for (int i = 1; i < data.length; i++) {
            double d = data[i] - data[i - 1];
            if (d > 0) minD = Math.min(minD, d);
        }
        f.setProperty("granularity", minD);
        return true;
    }

    /*
     * Calculates the centralized moment where
     * c is the center,
     * p is the power to raise to,
     * N is the total weight (the amount to divide by)
     */
    private static double moment(Double[] data, double c, int p, double N) {
        if (N <= 0) return Double.NaN;
        double sum = 0.0;
        for (Double element : data)
            sum += Math.pow(element - c, p);
        return sum / N;
    }

    private static double av(Double[] v, double index) {
        return (v[(int) Math.floor(index)] + v[(int) Math.ceil(index)]) / 2.0;
    }

    public static boolean creates(String key) {
        return "valid".equals(key) || "validNumeric".equals(key) || "mean".equals(key)
                || "stddev".equals(key) || "variance".equals(key)
                || "skew".equals(key) || "kurtosis".equals(key)
                || "min".equals(key) || "max".equals(key)
                || "q1".equals(key) || "q3".equals(key)
                || "median".equals(key) || "granularity".equals(key);
    }
}
