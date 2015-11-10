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
 * A geometric rectangle
 */
public class Rect {
    /* Coordinates */
    public final double x1, y1, x2, y2;

    public Rect(double x1, double x2, double y1, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        assert x2 >= x1 && y2 >= y1;
    }

    public final double area() {
        return (x2 - x1) * (y2 - y1);
    }

    public Point center() {
        return new Point(cx(), cy());
    }

    public Rect expand(double v) {
        return new Rect(x1 - width() * v, x2 + width() * v, y1 - height() * v, y2 + height() * v);
    }

    /**
     * Return eight points on the boundary of the rectangle
     *
     * @return array of eight [x,y] points on the boundary
     */
    public Point[] makeBoundaryPoints() {
        return new Point[]{
                new Point(x1, y1), new Point(cx(), y1), new Point(x2, y1), new Point(x2, cy()),
                new Point(x2, y2), new Point(cx(), y2), new Point(x1, y2), new Point(x1, cy()),
        };
    }

    public Rect union(Point p) {
        if (contains(p)) return this;
        return new Rect(Math.min(p.x, x1), Math.max(p.x, x2), Math.min(p.y, y1), Math.max(p.y, y2));
    }

    public double width() {
        return x2 - x1;
    }

    public double height() {
        return y2 - y1;
    }

    public final boolean contains(Point p) {
        return p.x >= x1 && p.y >= y1 && p.x <= x2 && p.y <= y2;
    }

    /**
     * Returns the x coordinate of the center of the rect
     *
     * @return the x coordinate of the center of the rect
     */
    public final double cx() {
        return (x1 + x2) / 2;
    }

    /**
     * Returns the y coordinate of the center of the rect
     *
     * @return the y coordinate of the center of the rect
     */
    public final double cy() {
        return (y1 + y2) / 2;
    }

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

        if (o.x1 + o.width() < x1) {
            // It is to our left
            if (o.y1 > y2) {
                // Completely in A -- find distance to corner
                return distancePtPt(o.x1 + o.width(), o.y1, x1, y2);
            } else if (o.y2 < y1) {
                // Completely in G -- find distance to corners
                return distancePtPt(o.x1 + o.width(), o.y2, x1, y1);
            } else {
                // Overlaps our sides -- the orthogonal distance is good
                return x1 - o.x1 - o.width();
            }
        } else if (o.x1 > x2) {
            // It is to our right
            if (o.y1 > y2) {
                // Completely in C -- find distance to corner
                return distancePtPt(o.x1, o.y1, x2, y2);
            } else if (o.y2 < y1) {
                // Completely in I -- find distance to corners
                return distancePtPt(o.x1, o.y2, x2, y1);
            } else {
                // Overlaps our sides -- the orthogonal distance is good
                return o.x1 - x2;
            }
        } else if (o.y1 > y2) {
            // Above us, but not completely in A or C, so horizontal extents overlap
            return o.y1 - y2;
        } else if (o.y2 < y1) {
            // Below us, but not completely in G or I, so horizontal extents overlap
            return y1 - o.y2;
        }

        if (contains(o) || o.contains(this)) {
            // One is contained in the other so find minimum distance between sides
            double d1 = Math.min(Math.abs(x1 - o.x1), Math.abs(x2 - o.x1 - o.width()));
            double d2 = Math.min(Math.abs(y1 - o.y1), Math.abs(y2 - o.y2));
            return -Math.min(d1, d2);
        }
        // They do intersect somehow and one is not contained in the other
        return 0;

    }

    private double distancePtPt(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    public final boolean contains(Rect o) {
        return o.x1 >= x1 && o.x2 <= x2 && o.y1 >= y1 && o.y2 <= y2;
    }

    public final double distanceTo(double px, double py) {
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

        if (py < y1) {
            if (px < x1) {
                // A: get distance to corner
                return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
            } else if (px > x2) {
                // C: get distance to corner
                return Math.sqrt((px - x2) * (px - x2) + (py - y1) * (py - y1));
            } else {
                // B: get distance to side
                return y1 - py;
            }
        } else if (py > y2) {
            if (px < x1) {
                // G: get distance to corner
                return Math.sqrt((px - x1) * (px - x1) + (py - y2) * (py - y2));
            } else if (px > x2) {
                // I: get distance to corner
                return Math.sqrt((px - x2) * (px - x2) + (py - y2) * (py - y2));
            } else {
                // H: get distance to side
                return py - y2;
            }
        } else {
            if (px < x1) {
                // D: get distance to side
                return x1 - px;
            } else if (px > x2) {
                // F: get distance to corner
                return px - x2;
            } else {
                // E: get minimum distance to a side; return as negative to denote inside
                return -Math.min(Math.min(px - x1, x2 - px), Math.min(py - y1, y2 - py));
            }
        }
    }

    public Rect intersection(Rect o) {
        double a1 = Math.max(x1, o.x1);
        double b1 = Math.max(y1, o.y1);
        double a2 = Math.min(x2, o.x2);
        double b2 = Math.min(y2, o.y2);
        return a2 > a1 && b2 > b1 ? new Rect(a1, a2, b1, b2) : null;
    }

    public final boolean intersects(Rect other) {
        return y2 >= other.y1 && y1 <= other.y2 && x2 >= other.x1 && x1 <= other.x2;
    }

    public Rect union(Rect o) {
        if (o == null || contains(o)) return this;
        if (o.contains(this)) return o;
        return new Rect(Math.min(x1, o.x1), Math.max(x2, o.x2), Math.min(y1, o.y1), Math.max(y2, o.y2));
    }

    public String toString() {
        return "[" + x1 + ", " + x2 + " : " + y1 + ", " + y2 + ")]";
    }

}
