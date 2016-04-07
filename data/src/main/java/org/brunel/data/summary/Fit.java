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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns a fitted value
 */
public abstract class Fit {
    private final Field fx, fy;                             // The fields
    private final Map<Object, Double> xCatMap, yCatMap;     // Field values to category indices

    protected final double[] x, y;                          // valid numeric pairs
    protected final double mx, my;                          // mean values


    public Fit(Field fy, Field fx, List<Integer> rows) {
        this.fx = fx;
        this.fy = fy;

        xCatMap = makeCatMap(fx);
        yCatMap = makeCatMap(fy);
        List<Double> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();
        for (int i : rows) {
            Double xv = vx(fx.value(i));
            Double yv = vy(fy.value(i));
            if (xv != null && yv != null) {
                xList.add(xv);
                yList.add(yv);
            }
        }

        int n = xList.size();
        Integer[] order = Data.order(xList.toArray(new Double[n]), true);
        this.x = new double[n];
        this.y = new double[n];
        for (int i = 0; i < n; i++) {
            this.x[i] = xList.get(order[i]);
            this.y[i] = yList.get(order[i]);
        }

        this.my = mean(y);
        this.mx = mean(x);

    }

    // Maps fields to their ordered numbers
    private Map<Object, Double> makeCatMap(Field f) {
        if (f.isNumeric()) return null;
        HashMap<Object, Double> map = new HashMap<>();
        Object[] categories = f.categories();
        for (int i = 0; i < categories.length; i++) map.put(categories[i], (double) i);
        return map;
    }

    public abstract Object get(Object value);

    protected Double vx(Object o) {
        return xCatMap == null ? Data.asNumeric(o) : xCatMap.get(o);
    }

    private static double mean(double[] values) {
        double s = 0;
        for (double value : values) s += value;
        return s / values.length;
    }


    protected Double vy(Object o) {
        return yCatMap == null ? Data.asNumeric(o) : yCatMap.get(o);
    }

    protected Object reverseY(double v) {
        if (yCatMap == null) return v;
        int n = (int) Math.round(v);
        Object[] categories = fy.categories();
        if (n<0 || n>= categories.length) return null;
        return categories[n];
    }

}
