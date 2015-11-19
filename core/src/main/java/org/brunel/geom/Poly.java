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

package org.brunel.geom;

import java.util.Collection;

/**
 * Very simple polygon class
 */
public class Poly {
    public final Point[] points;                // The points
    public final Rect bounds;                   // Their bounds

    /**
     * A polygons is a list of points
     *
     * @param points constituent parts
     */
    public Poly(Point... points) {
        this.points = points;
        this.bounds = Geom.bounds(points);
    }

    public Poly(Collection<Point> points) {
        this(points.toArray(new Point[points.size()]));
    }

    /**
     * See if the polygon contains the point.
     * This uses the winding number technique
     *
     * @param p point to test
     * @return true if it does
     */
    public boolean contains(Point p) {
        if (!bounds.contains(p)) return false;          // Quick check for speed

        // This method uses the ray casting algorithm described at
        // http://en.wikipedia.org/wiki/Point_in_polygon

        // It checks intersections of horizontal ray through y with the sides of the polygon
        // If there is an odd number of intersections to one side, the point is inside
        int intersectCountLeft = 0;

        // Go through each combination of coordinates
        // j will always point to the position before i
        int j = points.length - 1;

        // horizontal line: y = y
        for (int i = 0; i < points.length; i++) {
            // Determine line formula for current segment: y = mx + b
            double yj = points[j].y;
            double yi = points[i].y;
            double xj = points[j].x;
            double xi = points[i].x;
            double m = (yj - yi) / (xj - xi);

            if (Double.isNaN(m)) {
                // Both points are identical. Ignore this segment
            } else if (Math.abs(m) < 1e-6) {
                // two horizontal lines won't intersect (and if they do we don't want to increment anyway)
            } else {
                if (Math.abs(m) > 1e6) {
                    // Vertical line.`
                    double iy = p.y;

                    if ((iy > yi && iy < yj)
                            || (iy > yj - 0.0 && iy < yi)) {
                        // Is the intersection to the left or right?
                        if (xi < p.x) {
                            // If the horizontal ray hits a vertex, we have a
                            // special case.
                            if (Math.abs(iy - yi) == 0.0) {
                                // Only count if the other vertex on this segment is below the position.
                                if (yj > p.y) {
                                    intersectCountLeft++;
                                }
                            } else if (Math.abs(iy - yj) == 0.0) {
                                // Only count if the other vertex on this segment is below the position.
                                if (yi > p.y) {
                                    intersectCountLeft++;
                                }
                            } else {
                                intersectCountLeft++;
                            }
                        }
                    }
                } else {
                    // Solve for b: y - mx, using first point
                    double b = yi - m * xi;

                    // Find the x position of the intersection point of the segment
                    // with the horizontal ray.
                    // x = (y - b)/m
                    double ix = (p.y - b) / m;

                    if ((ix > xi && ix < xj)
                            || (ix > xj && ix < xi)) {

                        // Is the intersection to the left or right?
                        if (ix < p.x) {
                            // If the horizontal ray hits a vertex, we have a
                            // special case.
                            if (Math.abs(p.y - yi) == 0.0) {
                                // Only count if the other vertex on this segment is
                                // below the position.
                                // Screen coordinates - below is greater than.
                                if (yj > p.y) {
                                    intersectCountLeft++;
                                }
                            } else if (Math.abs(p.y - yj) == 0) {
                                // Only count if the other vertex on this segment is
                                // below the position.
                                if (yi > p.y) {
                                    intersectCountLeft++;
                                }
                            } else {
                                intersectCountLeft++;
                            }
                        }
                    }
                }
            }
            j = i;
        }

        return intersectCountLeft % 2 != 0;

    }

    public int size() {
        return points.length;
    }
}
