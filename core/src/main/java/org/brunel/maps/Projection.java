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

/**
 * A map projection
 */
abstract class Projection {

    private static double asRadians(double lon) {
        return Math.toRadians(lon);
    }

    /**
     * Rough estimate of the area of a small rectangle at the given location, when projected
     */
    public double getTissotArea(Point p) {
        double h = 5e-4;            // About 50m at the equator
        double dx = Math.abs(transform(p.translate(-h, 0)).x - transform(p.translate(h, 0)).x);
        double dy = Math.abs(transform(p.translate(0, -h)).y - transform(p.translate(0, h)).y);
        return dx * dy;
    }

    /**
     * Projects forward
     *
     * @param p point in lat/long coordinates
     * @return 2D screen coordinates
     */
    public abstract Point transform(Point p);

    /**
     * Projects backwards
     *
     * @param p the projected point
     * @return 2D longitude, latitude
     */
    public abstract Point inverse(Point p);

    /**
     * Provides a good guess at the size of a projected rectangle
     *
     * @param b rectangle to project
     * @return size in projected coordinate space
     */
    public Rect projectedExtent(Rect b) {
        Rect bounds = null;
        for (Point pt : b.makeBoundaryPoints()) {
            Point p = transform(pt);
            if (bounds == null) bounds = new Rect(p.x, p.x, p.y, p.y);
            else bounds = bounds.union(p);
        }
        return bounds;
    }

    public final static class Mercator extends Projection {
        public Point transform(Point p) {
            double a = asRadians(p.x);
            double b = asRadians(p.y);
            return new Point(a, Math.log(Math.tan(Math.PI / 4 + b / 2)));
        }

        public Point inverse(Point p) {
            double a = Math.toDegrees(p.x);
            double b = Math.toDegrees(2 * Math.atan(Math.exp(p.y)) - Math.PI / 2);
            return new Point(a, b);
        }

    }

    public final static class WinkelTripel extends Projection {
        public Point transform(Point p) {
            double x = asRadians(p.x);
            double y = asRadians(p.y);

            double a = Math.acos(Math.cos(y) * Math.cos(x / 2));
            double sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;

            return new Point(Math.cos(y) * Math.sin(x / 2) / sinca + x / Math.PI,
                    (Math.sin(y) * sinca + y) / 2);
        }

        public Point inverse(Point p) {
            throw new UnsupportedOperationException("Inverse not available for Winkel Triple");
        }

    }

    public final static class Albers extends Projection {

        private final double sin1, sin2, n;     // sins at parallels and their midpoint
        private final double C, r1;             // Other transform constants
        private final double rotation;          // Rotation for longitude

        public Albers(double parallelA, double parallelB, double rotation) {
            this.rotation = rotation;
            double s1 = asRadians(parallelA);
            double s2 = asRadians(parallelB);
            sin1 = Math.sin(s1);
            sin2 = Math.sin(s2);
            n = (sin1 + sin2) / 2;
            C = 1 + sin1 * (2 * n - sin1);
            r1 = Math.sqrt(C) / n;

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

}
