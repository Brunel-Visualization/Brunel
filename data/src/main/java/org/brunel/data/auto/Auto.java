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

package org.brunel.data.auto;

import org.brunel.data.Data;
import org.brunel.data.Field;

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
     * @param padFraction           amount to pad the raw range by
     * @param includeZeroTolerance include zero if it does not make "white space" more than this fraction
     * @param desiredTickCount     number of ticks it would be nice to get (<= 0 for auto)
     * @return the util on the scale
     */
    public static NumericScale makeNumericScale(Field f, boolean nice, double padFraction, double includeZeroTolerance, int desiredTickCount, boolean forBinning) {
        setTransform(f);

        // If the tick count is not set, calculate the optimal value, but no more than 20 bins
        if (desiredTickCount < 1) desiredTickCount = Math.min(optimalBinCount(f), 20) + 1;
        if (f.hasProperty("date")) return NumericScale.makeDateScale(f, nice, padFraction, desiredTickCount);
        String p = f.getStringProperty("transform");
        if (p.equals("log")) return NumericScale.makeLogScale(f, nice, padFraction, includeZeroTolerance);
        return NumericScale.makeLinearScale(f, nice, includeZeroTolerance, padFraction, desiredTickCount, forBinning);
    }

    public static void setTransform(Field f) {
        if (f.hasProperty("transform")) return;
        Double skew = f.getNumericProperty("skew");

        if (skew == null) {
            // Only numeric fields can have transforms
            f.setProperty("transform", "linear");
            return;
        }
        if (skew > 2 && f.min() > 0 && f.max() > 75 * f.min())
            f.setProperty("transform", "log");
        else if (skew > 1.0 && f.min() >= 0)
            f.setProperty("transform", "root");
        else
            f.setProperty("transform", "linear");
    }

    private static int optimalBinCount(Field f) {
        // Using Freedman-Diaconis for the optimal bin width OR Scott's normal reference rule
        // Whichever has a large bin size
        double h1 = 2 * (f.getNumericProperty("q3") - f.getNumericProperty("q1")) / Math.pow(f.valid(), 0.33333);
        double h2 = 3.5 * f.getNumericProperty("stddev") / Math.pow(f.valid(), 0.33333);
        double h = Math.max(h1, h2);
        if (h == 0)
            return 1;
        else
            return (int) Math.round((f.max() - f.min()) / h + 0.499);
    }

    public static double domainSimilarity(Field[] f1, Field[] f2) {
        Domain d1 = new Domain();
        Domain d2 = new Domain();
        for (Field f : f1) d1.add(f);
        for (Field f : f2) d2.add(f);
        return d1.mergedUnwastedSpace(d2);
    }

    public static Field convert(Field base) {
        // Must check this first, otherwise will be converted to numeric values
        double fractionDates = countFractionDates(base);
        if (fractionDates > FRACTION_TO_CONVERT) {
            // If any conversion needed, do so
            Field f = (fractionDates < 1.0) ? Data.toDate(base) : base;
            f.setProperty("numeric", true);
            f.setProperty("date", true);
            return f;
        }

        Field asNumeric = Data.toNumeric(base);
        if (asNumeric.valid() > FRACTION_TO_CONVERT * base.valid()) {
            // Check for special numeric range that looks like dates
            if (isYearly(asNumeric)) return Data.toDate(asNumeric, "year");
            // Otherwise this is good
            return asNumeric;
        }
        Field asDate = Data.toDate(base);
        if (asDate.valid() > FRACTION_TO_CONVERT * base.valid())
            return asDate;

        return base;
    }

    private static double countFractionDates(Field f) {
        double n = 0, s = 0;
        for (int i = 0; i < f.rowCount(); i++) {
            Object o = f.value(i);
            if (o != null) {
                if (o instanceof Date) s++;
                n++;
            }
        }
        return s / n;
    }

    private static boolean isYearly(Field asNumeric) {
        if (asNumeric.min() < 1900) return false;
        if (asNumeric.max() > 2100) return false;
        Double d = asNumeric.getNumericProperty("granularity");
        return d != null && d - Math.floor(d) < 1e-6;
    }
}
