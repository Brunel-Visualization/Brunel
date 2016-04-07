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

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Stack;

/**
 * Utility class for handling geometry
 */
public class Geom {

    /**
     * Create the convex hull of a set of points
     * @param pts a collection of points
     * @return a Polygon for the hull. This may be a degenerate polygon (with as few as zero points) but will not be null
     */
    public static Poly makeConvexHull(Collection<Point> pts) {
        // Get points, sorted by Y and then X
        Point[] points = pts.toArray(new Point[pts.size()]);
        Arrays.sort(points);

        // Stack to manipulate points with
        Stack<Point> stack = new Stack<>();
        if (points.length == 0) {
            return new Poly();
        }

        // Sort so bottom left is the first
        Point p0 = points[0];
        stack.push(p0);
        int N = points.length;

        // Sort by polar angle relative to p0
        Arrays.sort(points, 1, N, new PolarComparator(p0));

        // find next different point - p1
        int i = 1;
        while (i < N && p0.equals(points[i])) i++;
        if (i == N) return new Poly(p0);
        Point p1 = points[i];

        // find next point not collinear with p0,p1
        i++;
        while (i < N && Point.ccw(p0, p1, points[i]) == 0) i++;
        stack.push(points[i - 1]);        // This is the second point on the points

        // The main Graham Scan loop -- keep trying to add a new point to the end
        while (i < N) {
            Point top = stack.pop();
            while (Point.ccw(stack.peek(), top, points[i]) <= 0) top = stack.pop();
            stack.push(top);
            stack.push(points[i]);
            i++;
        }
        return new Poly(stack);
    }

    /**
     * Create the bounds for a set of point
     * @param points array of points
     * @return bounding rectangle (null if there are no points)
     */
    public static Rect bounds(Point[] points) {
        if (points.length == 0) return null;
        Rect r = new Rect(points[0].x, points[0].x, points[0].y, points[1].y);
        for (int i = 1; i < points.length; i++) r = r.union(points[i]);
        return r;
    }

    /**
     * Inter-point distance
     * @param x1 First Point, x coordinate
     * @param y1 First Point, y coordinate
     * @param x2 Second Point, x coordinate
     * @param y2 Second Point, y coordinate
     * @return Euclidean distance between the points
     */
    static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    /** Comparator used in convex hull to sort points by angle from a fix point */
    private static class PolarComparator implements Comparator<Point> {
        private final Point p;

        public PolarComparator(Point p) {
            this.p = p;
        }

        public int compare(Point q1, Point q2) {
            return -Point.ccw(p, q1, q2);
        }
    }

}
