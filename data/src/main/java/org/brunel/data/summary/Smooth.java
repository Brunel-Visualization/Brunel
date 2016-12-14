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

import org.brunel.data.Field;
import org.brunel.data.auto.Auto;

import java.util.List;

/**
 * Calculates a smooth fit function
 */
public class Smooth extends Fit {
    private final double window;                              // Window width for the data

    public Smooth(Field y, Field x, Double windowPercent, List<Integer> rows) {
        super(y, x, rows);
        this.window = getWindowWidth(x, windowPercent);
    }

    private double getWindowWidth(Field x, Double windowPercent) {
        double low;
        double high;
        if (x.isNumeric()) {
            low = x.min();
            high = x.max();
        } else {
            low = 0;
            high = x.categories().length - 1;
        }
        if (windowPercent != null) {
            return (high - low) * windowPercent / 200;
        } else {
            // use the optimal bin count to chose a window size
            int n = Auto.optimalBinCount(x);
            return (high - low) / n;
        }
    }

    public Object get(Object value) {
        Double at = vx(value);
        if (at == null) return null;
        return reverseY(calc(at, this.window));
    }

    private double calc(double at, double h) {
        int low = search(at - h, x);                   // low end of window
        int high = search(at + h, x);                  // high end of window

        double sy = 0, sw = 0;
        for (int i = low; i <= high; i++) {
            double d = (x[i] - at) / h;
            double w = 0.75 * (1 - d * d);
            if (w > 1e-5) {
                sw += w;
                sy += w * y[i];
            }
        }
        // If we have no data points, double the window size and try again
        // But if that would cause the window to be 10x bigger than requested, give up and use the mean
        if (sw < 1e-4) return h < window * 10 ? calc(at, h * 2) : my;
        return sy / sw;
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
