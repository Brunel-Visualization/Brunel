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
import org.brunel.data.auto.Auto;

/**
 * Calculates a smooth fit function
 */
public class Smooth implements Fit {
    private final double window;                              // Window width for the data
    private final double[] x, y;                              // x and y fields of data, sorted by x

    public Smooth(Field y, Field x, Double windowPercent) {
        if (windowPercent == null) {
            // use the optimal bin count to chose a window size
            int n = Auto.optimalBinCount(x);
            window = (x.max() - x.min()) / n;
        } else {
            window = (x.max() - x.min()) * windowPercent / 200;
        }
        double[][] pairs = Regression.asPairs(y, x);
        this.x = pairs[1];
        this.y = pairs[0];
    }

    public Double get(Object value) {
        Double at = Data.asNumeric(value);
        if (at == null) return null;

        int low = search(at - window, x);                   // low end of window
        int high = search(at + window, x);                  // high end of window

        double sy = 0, sw = 0;
        for (int i = low; i <= high; i++) {
            double d = (x[i] - at) / window;
            double w = 0.75 * (1 - d * d);
            sw += w;
            sy += w * y[i];
        }
        return sw > 0 ? sy / sw : null;
    }

    private int search(double at, double[] x) {
        // Binary search to find close point
        // constraint: x[p] <= at <= x[q]
        int p = 0;
        int q = x.length - 1;
        while (q - p > 1) {
            int t = p + q >> 1;
            if (x[t] <= at) p = t;
            if (x[t] >= at) q = t;
        }
        // make p and q point to the ends of a run of similar values
        while (p > 0 && x[p - 1] == at) p--;
        while (q < x.length - 1 && x[q + 1] == at) q++;
        return (p + q) >> 1;
    }
}
