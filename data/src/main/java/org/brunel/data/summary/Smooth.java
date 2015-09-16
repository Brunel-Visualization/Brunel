package org.brunel.data.summary;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;

/**
 * Calculates a smooth fit function
 */
public class Smooth implements Fit {
    private final double window;                                // Window to calculate smooth over
    private final double[][] data;                              // [y,x] fields of data, sorted

    public Smooth(Field y, Field x, double windowFactor) {
        int n = Auto.optimalBinCount(x);
        this.window = windowFactor * 1.5 * (x.max() - x.min()) / n;
        this.data = Regression.asPairs(y, x);
    }

    public Double get(Object value) {
        Double at = Data.asNumeric(value);
        if (at == null) return null;

        double[] y = data[0], x = data[1];

        double sy = 0, sw = 0;
        for (int i = 0; i < x.length; i++) {
            double d = (x[i] - at) / window;
            if (d<-1) continue;                         // Still searching forward for the first value
            if (d > 1) break;                           // Gone past all the possible data
            double w = 0.75 * (1 - d * d);
            sw += w;
            sy += w * y[i];
        }
        return sw > 0 ? sy / sw : null;
    }
}
