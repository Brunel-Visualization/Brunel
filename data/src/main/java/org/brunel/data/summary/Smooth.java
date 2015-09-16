package org.brunel.data.summary;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;

/**
 * Calculates a smooth fit function
 */
public class Smooth implements Fit {
    private final int delta;                                // Count of data either side to include in window
    private final double[][] data;                          // [y,x] fields of data, sorted

    public Smooth(Field y, Field x, Double windowPercent) {
        if (windowPercent == null) {
            // use the optimal bin count to chose a window size
            int n = Auto.optimalBinCount(x);
            delta = Math.max(2, Math.round((x.valid() / n)));
        } else {
            delta = Math.max(2, (int) (x.valid() * windowPercent / 200));
        }
        this.data = Regression.asPairs(y, x);
    }

    public Double get(Object value) {
        Double at = Data.asNumeric(value);
        if (at == null) return null;

        double[] y = data[0], x = data[1];
        int idx = search(at, x);                            // Closest index to the value 'at'
        int low = Math.max(0, idx - delta);                 // low end of window
        int high = Math.min(idx + delta, x.length - 1);     // high end of window
        double window = Math.max(at-x[low], x[high]-at);    // window size

        double sy = 0, sw = 0;
        for (int i = low; i <=high; i++) {
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
