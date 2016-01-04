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

package org.brunel.build.chart;

import org.brunel.action.Param;
import org.brunel.model.VisItem;

/**
 * Created by graham on 12/16/15.
 */
public class ChartLayout {
    /**
     * Default layouts, in percent coordinates. The outer array indexes the total number of charts, and
     * the next one indexes the chart within the order. The charts are assumed to be in importance order,
     * so the areas of the charts are decreasing. Layout coords are top, left, bottom, right
     */
    public static final double[][][] LAYOUTS = new double[][][]{
            {{0, 0, 100, 100}},                                                                          // One chart
            {{0, 0, 100, 50}, {0, 50, 100, 100}},                                                        // Two charts
            {{0, 0, 100, 50}, {0, 50, 50, 100}, {50, 50, 100, 100}},                                     // Three charts
            {{0, 0, 50, 50}, {0, 50, 50, 100}, {50, 0, 100, 50}, {50, 50, 100, 100}},                    // Four charts
    };
    private final double[][] locations;

    public ChartLayout(int width, int height, VisItem... charts) {
        this.locations = new double[charts.length][];

        double[][] unplacedLayouts = makeUnplacedLocations(width, height, charts);

        int nUnplaced = 0;
        for (int i = 0; i < charts.length; i++) {
            VisItem chart = charts[i];
            Param[] bounds = findBounds(chart);
            if (bounds == null) {
                // No bounds are given, so use the values from the pattern
                locations[i] = unplacedLayouts[nUnplaced++];
            } else {
                // Bounds are given so use them
                locations[i] = getLocation(bounds);
            }
        }

    }

    public double[] getLocation(int chartIndex) {
        return locations[chartIndex];
    }

    private Param[] findBounds(VisItem chart) {
        // Find the first item in the chart that has a bounds defined
        if (chart.children() == null) {
            return chart.getSingle().bounds;
        } else {
            for (VisItem v : chart.children()) {
                Param[] result = findBounds(v);
                if (result != null) return result;
            }
        }
        return null;
    }

    // Calculate the location for the bounds of the chart.
    private double[] getLocation(Param[] bounds) {
        double l = 0, t = 0, r = 100, b = 100;
        if (bounds != null && bounds.length > 0) l = bounds[0].asDouble();
        if (bounds != null && bounds.length > 1) t = bounds[1].asDouble();
        if (bounds != null && bounds.length > 2) r = bounds[2].asDouble();
        if (bounds != null && bounds.length > 3) b = bounds[3].asDouble();
        return new double[]{t, l, b, r};
    }

    private double[][] makeUnplacedLocations(int width, int height, VisItem[] charts) {
        int unplacedCount = 0;
        for (VisItem chart : charts)
            if (findBounds(chart) == null) unplacedCount++;
        if (unplacedCount == 0) return new double[0][];         // All locations are placed
        return squarify(ChartLayout.LAYOUTS[Math.min(unplacedCount - 1, 3)], width, height);
    }

    /* Swap dimensions if it makes the charts closer to the golden ration (1.62) */
    private double[][] squarify(double[][] layout, int width, int height) {
        double[][] alternate = new double[layout.length][];
        for (int i = 0; i < layout.length; i++)
            alternate[i] = new double[]{layout[i][1], layout[i][0], layout[i][3], layout[i][2]};
        return squarifyDivergence(alternate, width, height) < squarifyDivergence(layout, width, height)
                ? alternate : layout;
    }

    private double squarifyDivergence(double[][] rects, int width, int height) {
        double sum = 0;
        // Calculate weighted sum of divergence from the golden ratio
        for (double[] r : rects) {
            double h = Math.abs(r[1] - r[3]) * width;
            double v = Math.abs(r[0] - r[2]) * height;
            double div = (h / v - 1.62);
            sum += Math.sqrt(h * v) * div * div;
        }
        return sum;
    }

}
