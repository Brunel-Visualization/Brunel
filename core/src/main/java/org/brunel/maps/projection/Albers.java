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
 * Albers is good for around the poles
 */
class Albers extends Projection {

    private final double n;                 // midpoint of sins at parallels
    private final double C, r1;             // Other transform constants
    private final double parallelA;
    private final double parallelB;
    private final double rotation;          // Rotation for longitude

    public Albers(double parallelA, double parallelB, double rotation) {
        this.parallelA = parallelA;
        this.parallelB = parallelB;
        this.rotation = rotation;
        double s1 = asRadians(parallelA);
        double s2 = asRadians(parallelB);
        double sin1 = Math.sin(s1);
        double sin2 = Math.sin(s2);
        n = (sin1 + sin2) / 2;
        C = 1 + sin1 * (2 * n - sin1);
        r1 = Math.sqrt(C) / n;

    }

    public String d3Definition(Rect bounds) {

        String parallels = ".parallels([" + F.format(parallelA) + "," + F.format(parallelB) + "])";
        String rotate = ".rotate([" + F.format(rotation) + ",0,0])";

        Rect ext = transform(bounds);

        // We find the center in projected space, and then invert the projection
        Point c = inverse(ext.center());
        String center = ".center([0, " + F.format(c.y) + "])";

        return "d3.geo.albers()"
                + LN + translateDefinition()
                + LN + center
                + LN + parallels
                + LN + scaleDefinition(ext)
                + LN + rotate;
    }

    public Point transform(Point p) {
        double a = asRadians(p.x + rotation);
        double b = asRadians(p.y);
        double r = Math.sqrt(C - 2 * n * Math.sin(b)) / n;
        return new Point(r * Math.sin(a * n), r1 - r * Math.cos(a * n));
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public Point inverse(Point p) {
        double r1y = r1 - p.y;
        double lon = Math.atan2(p.x, r1y) / n;
        double v = (C - (p.x * p.x + r1y * r1y) * n * n) / (2 * n);
        double lat = Math.asin(Math.min(1, Math.max(-1, v)));
        return new Point(Math.toDegrees(lon) - rotation, Math.toDegrees(lat));
    }

}
