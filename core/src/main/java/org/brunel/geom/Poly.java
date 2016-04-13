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
     * @param points Collection of points in any order
     */
    public Poly(Collection<Point> points) {
        this(points.toArray(new Point[points.size()]));
    }

    /**
     * A polygons is a list of points.
     * This shares the array and does not copy it so do not modify the array post-creation
     *
     * @param points Array of points in any order
     */
    public Poly(Point... points) {
        this.points = points;
        this.bounds = Geom.bounds(points);
    }

    /**
     * Returns the number of points in the polygon
     *
     * @return size
     */
    public final int count() {
        return points.length;
    }

    public double distanceToBoundary(Point p) {
        int n = points.length;

        // calcuate distance to the ends of the line
        double dSq = Double.POSITIVE_INFINITY;
        for (Point q : points) dSq = Math.min(dSq, p.dist2(q));

        if (dSq < 1e-10) return 0;                  // We hit it -- no need to do anything else

        int j = n - 1;

        // Now need to handle each line segment
        for (int i = 0; i < n; i++) {
            double t = Geom.segmentPerpendicularDistanceSquared(p, points[i], points[j]);
            if (t < dSq) {
                dSq = t;                                        // This is closer than the best so far
                if (dSq < 1e-10) return 0;      // We hit it -- no need to do anything else
            }
            j = i;
        }
        return Math.sqrt(dSq);
    }

    /**
     * See if the polygon contains the point.
     * This uses the winding number technique
     *
     * @param p point to test
     * @return true if it does
     */
    public boolean contains(Point p) {

        // This check avoids the need for the complex call when outside the bounds

        // This check avoids the need for the complex call when outside the bounds
        if (!bounds.contains(p)) return false;

        // This method uses the ray casting algorithm described at
        // http://en.wikipedia.org/wiki/Point_in_polygon

        // It checks intersections of horizontal ray through y with the sides of the polygon
        // If there is an odd number of intersections to one side, the point is inside

        int intersectCountLeft = 0;
        int n = points.length;

        // Go through each combination of coordinates
        // j will always point to the position before i
        int j = n - 1;

        // horizontal line: y = y
        for (int i = 0; i < n; i++) {
            Point qi = points[i], qj = points[j];
            // Determine line formula for current segment: y = mx + b
            double m = (qj.y - qi.y) / (qj.x - qi.x);

            if (Double.isNaN(m)) {
                // Both points are identical. Ignore this segment
            } else if (Math.abs(m) == 0) {
                // two horizontal lines won't intersect (and if they do we don't want to increment anyway)
            } else if (Math.abs(m) > 1e20) {
                // Vertical line.
                double intersectX = qi.x;
                double intersectY = p.y;

                // Does it hit in the middle somewhere?
                if ((intersectY >= qi.y && intersectY <= qj.y) || (intersectY >= qj.y && intersectY <= qi.y)) {
                    // Is the intersection to the left or right?
                    if (intersectX < p.x) {
                        // If the horizontal ray hits a vertex, we have a
                        // special case.
                        if (intersectY == qi.y) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            if (qj.y > p.y) {
                                intersectCountLeft++;
                            }
                        } else if (intersectY == qj.y) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            if (qi.y > p.y) {
                                intersectCountLeft++;
                            }
                        } else {
                            intersectCountLeft++;
                        }
                    }
                }
            } else {
                // Solve for b: y - mx, using first point
                double b = qi.y - m * qi.x;

                // Find the x position of the intersection point of the segment
                // with the horizontal ray.
                // x = (y - b)/m
                double intersectX = (p.y - b) / m;

                if ((intersectX >= qi.x && intersectX <= qj.x) || (intersectX >= qj.x && intersectX <= qi.x)) {

                    // Is the intersection to the left or right?
                    if (intersectX < p.x) {
                        // If the horizontal ray hits a vertex, we have a
                        // special case.
                        if (p.y == qi.y) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            // Screen coordinates - below is greater than.
                            if (qj.y > p.y) {
                                intersectCountLeft++;
                            }
                        } else if (p.y == qj.y) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            if (qi.y > p.y) {
                                intersectCountLeft++;
                            }
                        } else {
                            intersectCountLeft++;
                        }
                    }
                }
            }
            j = i;
        }

        return intersectCountLeft % 2 != 0;


    }
}
