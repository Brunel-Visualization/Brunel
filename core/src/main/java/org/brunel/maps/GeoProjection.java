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

package org.brunel.maps;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Allows definitions of suitable geo projections
 */
public class GeoProjection {

    /**
     * This function defines a D3 projection to do the Winkel Tripel projection for a whole-earth projection
     */
    public static final String[] WinkelD3Function = new String[]{
            "function () {",
            "  function w(x, y) {",
            "    var a = Math.acos(Math.cos(y) * Math.cos(x / 2)), sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;",
            "    return [Math.cos(y) * Math.sin(x / 2) / sinca + x / Math.PI, (Math.sin(y) * sinca + y) / 2];",
            "  }",
            "  return d3.geo.projection(w);",
            "}"
    };

    public static final Projection MERCATOR = new Projection.Mercator();           // Mercator projection
    private static final Projection WINKEL3 = new Projection.WinkelTripel();        // Winkel Tripel
    private static final String LN = "\n\t\t";                                      // for output formatting
    private final String width;                                                     // JS name of width
    private final String height;                                                    // JS name of height
    private final String winkelTripleFunctionName;                                  // In JS, this will be the name
    private final NumberFormat F = new DecimalFormat("#.####");                     // for output formatting
    public String projectionName;                                                   // The name we chose

    /**
     * GeoProjection chooses good projections for D3 to use for a amp
     *
     * @param widthName                name of a Javascript variable that will contain the display width
     * @param heightName               name of a Javascript variable that will contain the display height
     * @param winkelTripleFunctionName the name to define as the winkel triple function
     */
    public GeoProjection(String widthName, String heightName, String winkelTripleFunctionName) {
        this.width = widthName;
        this.height = heightName;
        this.winkelTripleFunctionName = winkelTripleFunctionName;
    }

    public String makeProjection(Rect bounds) {
        // Are we USA only?
        if (bounds.x1 < -100 && bounds.y1 > 17 && bounds.y2 > 35 && bounds.y2 < 73) {
            // If we need Alaska and/or Hawaii use the AlbersUSA, otherwise plain Mercator
            if (bounds.x1 < -120) return makeAlbersUSA();
            else return makeMercator(bounds);
        }

        // Mercator if the distortion is tolerable
        if (getMercatorDistortion(bounds) <= 1.8) return makeMercator(bounds);

        // If we cover a wide area, use winkel triple
        if (bounds.x2 - bounds.x1 > 180 && bounds.y2 - bounds.y1 > 90) return makeWinkelTripel(bounds);

        // Otherwise albers is our best bet
        return makeAlbers(bounds);

    }

    private String makeAlbersUSA() {
        this.projectionName = "albersUSA";
        // Everything is well known, so easy to define with fixed details
        return "d3.geo.albersUsa()"
                + LN + ".scale(Math.min(" + width + "/0.96, " + height + "/0.48))"
                + LN + translateDefinition();
    }

    private String makeMercator(Rect b) {
        this.projectionName = "mercator";
        Rect ext = MERCATOR.projectedExtent(b);

        // We find the center in projected space, and then invert the projection
        Point c = MERCATOR.inverse(ext.center());
        String center = ".center([" + F.format(c.x) + ", " + F.format(c.y) + "])";

        return "d3.geo.mercator()"
                + LN + translateDefinition()
                + LN + scaleDefinition(ext)
                + LN + center;
    }

    /**
     * The distortion is the ratio of the projected areas of a small rectangle between the top and bottom
     * of the projected area.
     *
     * @param bounds area to look at
     * @return Ratio always greater than 1
     */
    private double getMercatorDistortion(Rect bounds) {
        if (bounds.y1 < -89 || bounds.y2 > 89) return 100;
        double a = MERCATOR.getTissotArea(new Point(bounds.cx(), bounds.y1));
        double b = MERCATOR.getTissotArea(new Point(bounds.cx(), bounds.y2));
        return a < b / 100 || b < a / 100 ? 100 : Math.max(b / a, a / b);
    }

    private String makeWinkelTripel(Rect bounds) {
        this.projectionName = "winkelTripel";
        Rect ext = WINKEL3.projectedExtent(bounds);

        // Finding the center is tricky because we cannot invert the transform so we have to search for it
        // We just do a grid search; slow, but simple. First by y, then by x
        double y = 0, dy = 9e99;
        for (int i = -90; i < 90; i++) {
            Point p = WINKEL3.transform(new Point(bounds.cx(), i));
            double dp = Math.abs(p.y - ext.cy());
            if (dp < dy) {
                dy = dp;
                y = i;
            }
        }

        double x = 0, dx = 9e99;
        for (int i = -180; i < 180; i++) {
            Point p = WINKEL3.transform(new Point(i, y));
            double dp = Math.abs(p.x - ext.cx());
            if (dp < dx) {
                dx = dp;
                x = i;
            }
        }

        String center = ".center([" + F.format(x) + ", " + F.format(y) + "])";
        return winkelTripleFunctionName + "()"
                + LN + translateDefinition()
                + LN + scaleDefinition(ext)
                + LN + center;
    }

    private String makeAlbers(Rect b) {
        this.projectionName = "albers";

        // Parallels at 1/6 and 5/6 of the latitude
        double parallelA = (b.y1 + b.y2 * 5) / 6;           // Parallels at 1/6 and 5/6
        double parallelB = (b.y1 * 5 + b.y2) / 6;           // Parallels at 1/6 and 5/6
        double angle = -b.cx();                             // Rotation angle

        Projection projection = new Projection.Albers(parallelA, parallelB, angle);

        String parallels = ".parallels([" + F.format(parallelA) + "," + F.format(parallelB) + "])";
        String rotate = ".rotate([" + F.format(angle) + ",0,0])";

        Rect ext = projection.projectedExtent(b);

        // We find the center in projected space, and then invert the projection
        Point c = projection.inverse(ext.center());
        String center = ".center([0, " + F.format(c.y) + "])";

        return "d3.geo.albers()"
                + LN + translateDefinition()
                + LN + center
                + LN + parallels
                + LN + scaleDefinition(ext)
                + LN + rotate;
    }

    private String translateDefinition() {
        return ".translate([" + width + "/2, " + height + "/2])";
    }

    private String scaleDefinition(Rect extent) {
        return ".scale(Math.min((" + width + "-4)/" + F.format(extent.width())
                + ", (" + height + "-4)/" + F.format(extent.height()) + "))";
    }
}
