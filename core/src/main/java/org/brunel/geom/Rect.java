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

/**
 * A geometric rectangle
 */
public class Rect {
    /**
     * Coordinates of the rectangle
     */
    public final double left, top, right, bottom;

    public Rect(double left, double right, double top, double bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        assert right >= left && bottom >= top;
    }

    /**
     * Area
     *
     * @return area of rectangle
     */
    public final double area() {
        return (right - left) * (bottom - top);
    }

    /**
     * Simple center
     *
     * @return rectangle center
     */
    public final Point center() {
        return new Point(cx(), cy());
    }

    /**
     * Returns the x coordinate of the center of the rect
     *
     * @return the x coordinate of the center of the rect
     */
    public final double cx() {
        return (left + right) / 2;
    }

    /**
     * Returns the y coordinate of the center of the rect
     *
     * @return the y coordinate of the center of the rect
     */
    public final double cy() {
        return (top + bottom) / 2;
    }

    /**
     * Minimum distance between this and another rectangle
     *
     * @param o other rectangle
     * @return &gt; 0 if they are outside, &lt;0 if one is contained in the other, 0 if the bounds intersect
     */
    public final double distance(Rect o) {

		/*
         * Base algorithm works out where the other rect is relative to this one
		 * Most common cases are off the corners or edges
		 *
		 * 			A						C
		 * 				-----------------
		 * 				|				|
		 * 				|				|
		 * 				|				|
		 * 				-----------------
		 * 			G						I
		 */

        if (o.right < left) {
            // It is to our left
            if (o.top > bottom) {
                // Completely in A -- find distance to corner
                return Geom.distance(o.right, o.top, left, bottom);
            } else if (o.bottom < top) {
                // Completely in G -- find distance to corners
                return Geom.distance(o.right, o.bottom, left, top);
            } else {
                // Overlaps our sides -- the orthogonal distance is good
                return left - o.left - o.width();
            }
        } else if (o.left > right) {
            // It is to our right
            if (o.top > bottom) {
                // Completely in C -- find distance to corner
                return Geom.distance(o.left, o.top, right, bottom);
            } else if (o.bottom < top) {
                // Completely in I -- find distance to corners
                return Geom.distance(o.left, o.bottom, right, top);
            } else {
                // Overlaps our sides -- the orthogonal distance is good
                return o.left - right;
            }
        } else if (o.top > bottom) {
            // Above us, but not completely in A or C, so horizontal extents overlap
            return o.top - bottom;
        } else if (o.bottom < top) {
            // Below us, but not completely in G or I, so horizontal extents overlap
            return top - o.bottom;
        }

        if (contains(o) || o.contains(this)) {
            // One is contained in the other so find minimum distance between sides
            double d1 = Math.min(Math.abs(left - o.left), Math.abs(right - o.right));
            double d2 = Math.min(Math.abs(top - o.top), Math.abs(bottom - o.bottom));
            return -Math.min(d1, d2);
        }
        // They do intersect somehow and one is not contained in the other
        return 0;

    }

    public double width() {
        return right - left;
    }

    private boolean contains(Rect o) {
        return o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom;
    }

    /**
     * Minimum distance to a point
     *
     * @param p point to calculate distance t
     * @return Distance, with negative meaning the point is inside the rect
     */
    public final double distanceTo(Point p) {
        /*
         * Base algorithm on comparisons to see where the point is relative to the rect
		 *
		 * 			A			B			C
		 * 				-----------------
		 * 				|				|
		 * 			D	|		E		|	F
		 * 				|				|
		 * 				-----------------
		 * 			G			H			I
		 */

        if (p.y < top) {
            if (p.x < left) {
                // A: get distance to corner
                return Math.sqrt((p.x - left) * (p.x - left) + (p.y - top) * (p.y - top));
            } else if (p.x > right) {
                // C: get distance to corner
                return Math.sqrt((p.x - right) * (p.x - right) + (p.y - top) * (p.y - top));
            } else {
                // B: get distance to side
                return top - p.y;
            }
        } else if (p.y > bottom) {
            if (p.x < left) {
                // G: get distance to corner
                return Math.sqrt((p.x - left) * (p.x - left) + (p.y - bottom) * (p.y - bottom));
            } else if (p.x > right) {
                // I: get distance to corner
                return Math.sqrt((p.x - right) * (p.x - right) + (p.y - bottom) * (p.y - bottom));
            } else {
                // H: get distance to side
                return p.y - bottom;
            }
        } else {
            if (p.x < left) {
                // D: get distance to side
                return left - p.x;
            } else if (p.x > right) {
                // F: get distance to corner
                return p.x - right;
            } else {
                // E: get minimum distance to a side; return as negative to denote inside
                return -Math.min(Math.min(p.x - left, right - p.x), Math.min(p.y - top, bottom - p.y));
            }
        }
    }

    /**
     * Increase size by a ratio.
     * The height and width are increased by a fraction of the width and height.
     * A negative number will inset, but it must be greater than -1, otherwise the result will be an illegal rectangle.
     *
     * @param v expansion ratio, greater than -1
     * @return new rectangle
     */
    public Rect expand(double v) {
        return new Rect(left - width() * v, right + width() * v, top - height() * v, bottom + height() * v);
    }

    public double height() {
        return bottom - top;
    }

    public final boolean intersects(Rect other) {
        return bottom >= other.top && top <= other.bottom && right >= other.left && left <= other.right;
    }

    /**
     * Return eight points on the boundary of the rectangle
     *
     * @return array of eight [x,y] points on the boundary
     */
    public Point[] makeBoundaryPoints() {
        return new Point[]{
                new Point(left, top), new Point(cx(), top), new Point(right, top), new Point(right, cy()),
                new Point(right, bottom), new Point(cx(), bottom), new Point(left, bottom), new Point(left, cy()),
        };
    }

    public String toString() {
        return "[" + left + ", " + right + " : " + top + ", " + bottom + ")]";
    }

    public Rect union(Point p) {
        if (contains(p)) return this;
        return new Rect(Math.min(p.x, left), Math.max(p.x, right), Math.min(p.y, top), Math.max(p.y, bottom));
    }

    public final boolean contains(Point p) {
        return p.x >= left && p.y >= top && p.x <= right && p.y <= bottom;
    }

    /**
     * Create a rectangle containing two other rectangles
     * @param a rectangle, may be null
     * @param b rectangle, may be null
     * @return rectangle, will be null when both parameters are null
     */
    public static Rect union(Rect a, Rect b) {
        if (a == null) return b;            // Nulls are ignored
        if (b == null) return a;            // Nulls are ignored
        if (a.contains(b)) return a;        // If one contains another, do not create a new object
        if (b.contains(a)) return b;        // If one contains another, do not create a new object
        return new Rect(Math.min(a.left, b.left), Math.max(a.right, b.right),
                Math.min(a.top, b.top), Math.max(a.bottom, b.bottom));
    }

}
