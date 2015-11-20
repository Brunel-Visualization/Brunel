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

package org.brunel.maps.projection;

import org.brunel.geom.Point;
import org.brunel.geom.Rect;

/**
 * Allows definitions of suitable geo projections
 */
public class ProjectionBuilder {

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

    public static final Projection MERCATOR = new Mercator();           // Mercator projection
    public static final Projection WINKEL3 = new WinkelTripel();        // Winkel Tripel
    public static final Projection ALBERS_USA = new AlbersUSA();        // Albers for the U.S.A.

    public static Projection makeProjection(Rect bounds) {
        // Are we USA only?
        if (bounds.x1 < -100 && bounds.y1 > 17 && bounds.y2 > 35 && bounds.y2 < 73) {
            // If we need Alaska and/or Hawaii use the AlbersUSA, otherwise plain Mercator
            if (bounds.x1 < -120) return ALBERS_USA;
            else return MERCATOR;
        }

        // Mercator if the distortion is tolerable
        if (getMercatorDistortion(bounds) <= 1.8) return MERCATOR;

        // If we cover a wide area, use winkel triple
        if (bounds.x2 - bounds.x1 > 180 && bounds.y2 - bounds.y1 > 90) return WINKEL3;

        // Otherwise albers is our best bet
        return makeAlbers(bounds);

    }

    /**
     * The distortion is the ratio of the projected areas of a small rectangle between the top and bottom
     * of the projected area.
     *
     * @param bounds area to look at
     * @return Ratio always greater than 1
     */
    private static double getMercatorDistortion(Rect bounds) {
        if (bounds.y1 < -89 || bounds.y2 > 89) return 100;
        double a = MERCATOR.getTissotArea(new Point(bounds.cx(), bounds.y1));
        double b = MERCATOR.getTissotArea(new Point(bounds.cx(), bounds.y2));
        return a < b / 100 || b < a / 100 ? 100 : Math.max(b / a, a / b);
    }

    private static Albers makeAlbers(Rect b) {
        // Parallels at 1/6 and 5/6 of the latitude
        double parallelA = (b.y1 + b.y2 * 5) / 6;           // Parallels at 1/6 and 5/6
        double parallelB = (b.y1 * 5 + b.y2) / 6;           // Parallels at 1/6 and 5/6
        double angle = -b.cx();                             // Rotation angle
        return new Albers(parallelA, parallelB, angle);
    }
}
