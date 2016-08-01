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

package org.brunel.data.auto;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.util.ItemsList;

import java.util.Date;

/**
 * Contains a number of static methods for automatic processing.
 * This includes determining suitable ticking structure and domains for axes
 */
public class Auto {
    private static final double FRACTION_TO_CONVERT = 0.5;

    /**
     * Create information to build a good scale.
     * For the includeZeroTolerance parameter (call it p) we will add zero to the scale if the amount of white space this
     * would add to the chart is less than a fraction p.
     * Thus 0 will mean "never", 1 will mean "always"
     *
     * @param f                    the scale to use
     * @param nice                 whether to make the limits a nice number
     * @param padFraction          amount to pad the raw range by (upper and lower values)
     * @param includeZeroTolerance include zero if it does not make "white space" more than this fraction
     * @param desiredTickCount     number of ticks it would be nice to get (&lt;= 0 for auto)
     * @return the util on the scale
     */
    public static NumericScale makeNumericScale(Field f, boolean nice, double[] padFraction, double includeZeroTolerance, int desiredTickCount, boolean forBinning) {
        setTransform(f);

        // If the tick count is not set, calculate the optimal value, but no more than 20 bins
        if (desiredTickCount < 1) desiredTickCount = Math.min(optimalBinCount(f), 20) + 1;
        if (f.isDate()) return NumericScale.makeDateScale(f, nice, padFraction, desiredTickCount);
        String p = f.strProperty("transform");
        if (p.equals("log"))
            return NumericScale.makeLogScale(f, nice, padFraction, includeZeroTolerance, desiredTickCount);

        // We need to modify the scale for a root transform, as we need a smaller pad fraction near zero
        // as that will show more space than expected
        if (p.equals("root")) {
            if (f.min() > 0) {
                double scaling = (f.min() / f.max()) / (Math.sqrt(f.min()) / Math.sqrt(f.max()));
                includeZeroTolerance *= scaling;
                padFraction[0] *= scaling;
            }
        }

        return NumericScale.makeLinearScale(f, nice, includeZeroTolerance, padFraction, desiredTickCount, forBinning);
    }

    public static void setTransform(Field f) {
        if (f.property("transform") != null) return;
        Double skew = f.numProperty("skew");

        if (skew == null) {
            // Only numeric fields can have transforms
            f.set("transform", "linear");
            return;
        }
        if (skew > 2 && f.min() > 0 && f.max() > 75 * f.min())
            f.set("transform", "log");
        else if (skew > 1.0 && f.min() >= 0)
            f.set("transform", "root");
        else
            f.set("transform", "linear");
    }

    public static int optimalBinCount(Field f) {
        // For non-numeric data
        if (!f.isNumeric()) return Math.min(7, f.categories().length);

        // Using Freedman-Diaconis for the optimal bin width OR Scott's normal reference rule
        // Whichever has a large bin size
        double h1 = 2 * (f.numProperty("q3") - f.numProperty("q1")) / Math.pow(f.valid(), 0.33333);
        double h2 = 3.5 * f.numProperty("stddev") / Math.pow(f.valid(), 0.33333);
        double h = Math.max(h1, h2);
        if (h == 0)
            return 1;
        else
            return (int) Math.round((f.max() - f.min()) / h + 0.499);
    }

    public static Field convert(Field base) {
        if (base.isSynthetic() || base.isDate()) return base;           // Already set
        if (base.isProperty("list")) return base;                     // Already a multi-set


        // Try conversion to a lists
        Field asList = Data.toList(base);
        if (goodLists(asList)) return asList;

        int N = base.valid();

        // Create a random order
        int[] order = new int[base.rowCount()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = 0; i < order.length; i++) {
            int j = (int) Math.floor(Math.random() * (order.length - i));
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }

        // Try conversion to numeric
        Field asNumeric;
        if (base.isNumeric()) {
            asNumeric = base;
        } else {
            int n = 0, i = 0;
            int nNumeric = 0;
            while (n < N && n < 50) {
                Object o = base.value(order[i++]);
                if (o == null) continue;
                n++;
                if (!(o instanceof Date) && Data.asNumeric(o) != null) nNumeric++;
            }
            asNumeric = nNumeric > FRACTION_TO_CONVERT * n ? Data.toNumeric(base) : null;
        }

        if (asNumeric != null) {
            // See if the numeric results are years
            if (isYearly(asNumeric)) return Data.toDate(asNumeric, "year");
            // Otherwise this is good
            return asNumeric;
        }

        // Try conversion to dates
        int n = 0, i = 0;
        int nDate = 0;
        while (n < N && n < 50) {
            Object o = base.value(order[i++]);
            if (o == null) continue;
            n++;
            if (Data.asDate(o) != null) nDate++;
        }

        if (nDate > FRACTION_TO_CONVERT * n)
            return Data.toDate(base);

        return base;
    }

    private static boolean goodLists(Field f) {
        int nValid = f.valid();
        if (nValid < 3) return false;                                   // Too few to autoconvert
        // We need at least one different length list
        int n = -1;
        for (int i = 1; i < f.rowCount(); i++) {
            ItemsList o = (ItemsList) f.value(i);
            if (o == null) continue;
            if (n < 0)
                n = o.size();
            else if (o.size() != n) {
                // Lists of different length -- good!
                // With small number of rows, assume OK
                if (nValid < 20) return true;
                // Otherwise see if the list categories strongly reduce the others
                int nList = ((Object[]) f.property("listCategories")).length;
                return (nList * nList < nValid * 2);
            }
        }
        return false;                                                  // All lists of the same length

    }

    private static boolean isYearly(Field asNumeric) {
        if (asNumeric.numProperty("q1") < 1600) return false;           // Use the lower quartile (to avoid outliers)
        if (asNumeric.numProperty("q3") > 2100) return false;           // High value is usually OK
        Double d = asNumeric.numProperty("granularity");
        return d != null && d - Math.floor(d) < 1e-6;
    }
}
