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
import org.brunel.data.stats.DateStats;
import org.brunel.data.util.DateUnit;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class contains methods to define a numeric scale, called via static methods
 * They are each initialized using a field, a boolean 'nice' to indicate whether to expand the range to nice numbers,
 * a fractional amount to pad the field's domain by (e.g. 0.02) and a desired number of ticks,
 * as well as other parameters for specific scales.
 *
 * When called, the effect is to return a NumericScale that has all properties set to useful values
 */
public class NumericScale {

    public static NumericScale makeDateScale(Field f, boolean nice, double[] padFraction, int desiredTickCount) {
        double a = f.min();
        double b = f.max();

        if (a == b) {
            DateUnit unit = (DateUnit) f.property("dateUnit");
            a = Data.asNumeric(DateUnit.increment(Data.asDate(a), unit, -1));
            b = Data.asNumeric(DateUnit.increment(Data.asDate(b), unit, 1));
        } else {
            a -= padFraction[0] * (b - a);
            b += padFraction[1] * (b - a);
        }
        double desiredDaysGap = (b - a) / (desiredTickCount - 1);
        DateUnit unit = DateStats.getUnit(desiredDaysGap * 4);

        int multiple = NumericScale.bestDateMultiple(unit, desiredDaysGap);

        // x is the nice lower value
        Date x = DateUnit.floor(Data.asDate(a), unit, multiple);
        if (nice) a = Data.asNumeric(x);

        List<Double> d = new ArrayList<Double>();
        while (true) {
            double v = Data.asNumeric(x);
            if (v >= b) {
                // We have come to the end of the range
                if (nice || v == b) {
                    // If we want a nice upper, or this value happens to be exactly the nice upper, add this in
                    b = v;
                    d.add(v);
                }
                break;
            }
            if (v >= a) d.add(v);
            x = DateUnit.increment(x, unit, multiple);
        }

        if (nice) b = Data.asNumeric(x);

        Double[] data = d.toArray(new Double[d.size()]);
        return new NumericScale("date", a, b, data, false);
    }

    private static int bestDateMultiple(DateUnit unit, double desiredDaysGap) {
        double target = desiredDaysGap / unit.approxDaysPerUnit;
        int multiple = 1;
        // Try all other multiples that divide evenly into the base
        for (int i = 2; i <= unit.base / 2; i++) {
            if (unit.base % i != 0) continue;
            if (i == 4) continue;                                // This isn't good in practice
            if (i == 6 && unit.base == 60) continue;            // This isn't good in practice
            if (Math.abs(target - i) <= Math.abs(target - multiple))
                multiple = i;
        }
        return multiple;
    }

    public static NumericScale makeLinearScale(Field f, boolean nice, double includeZeroTolerance, double[] padFraction, int desiredTickCount, boolean forBinning) {
        // Guard against having no data
        if (f.valid() == 0) return new NumericScale("linear", 0, 1, new Double[]{0.0, 1.0}, false);

        double a0 = f.min();
        double b0 = f.max();

        double padA = padFraction[0] * (b0 - a0);
        double padB = padFraction[1] * (b0 - a0);

        double a = a0 - padA;
        double b = b0 + padB;

        // Include zero if it doesn't expand too much
        if (a > 0 && a / b <= includeZeroTolerance) a = 0;
        if (b < 0 && b / a <= includeZeroTolerance) b = 0;

        // Handle nice ranges that we want to keep very nice
        if (a == 0) {
            if (b0 <= 1 + 1e-4 && b > 1) b = 1;                       // 0 - 1
            if (b0 < 100 + 1e-3 && b > 100) b = 100;                  // 0 - 100
        }

        // For degenerate data expand out
        if (a + 1e-6 > b) {
            b = Math.max(0, 2 * a);
            a = Math.min(0, 2 * a);
        }

        double desiredDivCount = Math.max(desiredTickCount - 1, 1);

        String transform = f.stringProperty("transform");
        double granularity = f.numericProperty("granularity");
        double granularDivs = (b - a) / granularity;
        if ((forBinning || f.preferCategorical()) && granularDivs > desiredDivCount / 2 && granularDivs < desiredDivCount * 2) {
            Double[] data = makeGranularDivisions(a, b, granularity, nice);
            return new NumericScale(transform, a, b, data, true);
        }

        // Work out a likely delta based on powers of ten
        double rawDelta = (b - a) / desiredDivCount;
        double deltaLog10 = Math.floor(Math.log(rawDelta) / Math.log(10));
        double delta = Math.pow(10, deltaLog10);

        // Then look around that multiple, using decimal-friendly multipliers
        double bestDiff = 1e9;
        double[] choices = new double[]{delta, delta * 10, delta / 10, delta * 5, delta / 2, delta * 2, delta / 5};
        for (double d : choices) {
            double low = d * Math.ceil(a / d);
            double high = d * Math.floor(b / d);
            double dCount = Math.round((high - low) / d) + 1;
            if (nice && a < low) dCount++;
            if (nice && b > high) dCount++;
            double diff = Math.abs(dCount - desiredTickCount);
            if (dCount > desiredTickCount) diff -= 0.001;            // For ties, prefer one more
            if (diff < bestDiff) {
                bestDiff = diff;
                delta = d;
            }
        }

        // x is the nice lower value; it may be set as the actual, lower value if niceLower is true
        double x = delta * Math.floor(a / delta);
        if (nice) {
            a = x;
            b = delta * Math.ceil(b / delta);
        }

        // Make sure x >= a and then add ticks up until we hit b
        if (x < a - 1e-6) x += delta;
        List<Double> d = new ArrayList<Double>();
        while (x < b + 1e-6) {
            d.add(x);
            x += delta;
        }

        Double[] data = d.toArray(new Double[d.size()]);
        return new NumericScale(transform, a, b, data, false);
    }

    private static Double[] makeGranularDivisions(double min, double max, double granularity, boolean nice) {
        List<Double> div = new ArrayList<Double>();
        if (!nice) {
            // inside the bounds only
            min += granularity;
            max -= granularity;
        }
        double at = min - granularity / 2;
        while (at < max + granularity) {
            div.add(at);
            at += granularity;
        }
        return div.toArray(new Double[div.size()]);
    }

    public static NumericScale makeLogScale(Field f, boolean nice, double[] padFraction, double includeZeroTolerance) {
        double a = Math.log(f.min()) / Math.log(10);
        double b = Math.log(f.max()) / Math.log(10);

        a -= padFraction[0] * (b - a);
        b += padFraction[1] * (b - a);

        // Include zero (actually one in untransformed space) if it doesn't expand too much
        if (a > 0 && a / b <= includeZeroTolerance) a = 0;

        if (nice) {
            a = Math.floor(a);
            b = Math.ceil(b);
        }

        List<Double> d = new ArrayList<Double>();
        for (int i = (int) Math.ceil(a); i <= b + 1e-6; i++)
            d.add(Math.pow(10, i));
        Double[] data = d.toArray(new Double[d.size()]);
        return new NumericScale("log", Math.pow(10, a), Math.pow(10, b), data, false);
    }

    public final Double[] divisions;
    public final double max;
    public final double min;
    public final String type;
    public final boolean granular;

    private NumericScale(String type, double min, double max, Double[] divs, boolean granular) {
        this.type = type;
        this.min = min;
        this.max = max;
        divisions = divs;
        this.granular = granular;
    }

}
