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
 * Suitable for whole-world projection, cannot be inverted
 */
class WinkelTripel extends Projection {
    public String d3Definition(Rect bounds) {
        Rect ext = transform(bounds);

        // Finding the center is tricky because we cannot invert the transform so we have to search for it
        // We just do a grid search; Slow, but simple. First by y (using the center as the x, then by x
        double y = 0, dy = 9e99;
        for (int i = -90; i < 90; i++) {
            Point p = transform(new Point(bounds.cx(), i));
            double dp = Math.abs(p.y - ext.cy());
            if (dp < dy) {
                dy = dp;
                y = i;
            }
        }

        double x = 0, dx = 9e99;
        for (int i = -180; i < 180; i++) {
            Point p = transform(new Point(i, y));
            double dp = Math.abs(p.x - ext.cx());
            if (dp < dx) {
                dx = dp;
                x = i;
            }
        }

        String center = ".center([" + F.format(x) + ", " + F.format(y) + "])";
        return "winkel3()"
                + LN + translateDefinition()
                + LN + scaleDefinition(ext)
                + LN + center;
    }

    public Point transform(Point p) {
        double x = Math.toRadians(p.x);
        double y = Math.toRadians(p.y);

        double a = Math.acos(Math.cos(y) * Math.cos(x / 2));
        double sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;

        return new Point(Math.cos(y) * Math.sin(x / 2) / sinca + x / Math.PI,
                (Math.sin(y) * sinca + y) / 2);
    }

    public Point inverse(Point p) {
        throw new UnsupportedOperationException("Inverse not available for Winkel Triple");
    }

}
