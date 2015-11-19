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

import org.brunel.geom.Geom;
import org.brunel.geom.Point;
import org.brunel.geom.Rect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects points so as to make both bounds and a convex points
 */
class PointCollection {

    private final Set<Point> points = new HashSet<Point>();         // Collect the points as they come in
    private Rect bounds;                                            // Create the bounds
    private List<Point> hull;                                           // The convex points

    public void add(double x, double y) {
        points.add(new Point(x, y));
    }

    public Rect bounds() {
        if (bounds == null) build();
        return bounds;
    }

    private void build() {
        hull = Geom.makeConvexHull(points);            // Build the points
        bounds = makeBounds();                  // use that for the bounds
        points.clear();                         // Throw away old points
        points.addAll(hull);                     // All we need is the points
    }

    private Rect makeBounds() {
        Rect bounds = null;
        for (Point p : hull) {
            if (bounds == null) bounds = new Rect(p.x, p.x, p.y, p.y);
            else bounds = bounds.union(p);
        }
        return bounds;
    }

    public List<Point> convexHull() {
        if (hull == null) build();
        return hull;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }


}
