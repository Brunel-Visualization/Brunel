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
 * Keeps basic information on a GeoJSON file
 */
class GeoFile implements Comparable<GeoFile> {
    public final String name;           // File name
    public final double size;           // Size in Kbytes
    public final Rect bounds;           // longitude min, max; latitude min,max
    private final Poly hull;            // Convex points in lat/long

    /**
     * Defines a GeoFile
     *
     * @param name         file name (not including extension or path). EG "world"
     * @param boundsString x1,x2,y1,y2
     * @param hullString   A polygon: x1,x2;y1,y2;...
     * @param sizeString   An integer giving the file download size in K
     */
    public GeoFile(String name, String boundsString, String hullString, String sizeString) {
        this.name = name;
        this.size = Integer.parseInt(sizeString);
        String[] b = boundsString.split(",");
        this.bounds = new Rect(Double.parseDouble(b[0]), Double.parseDouble(b[1]), Double.parseDouble(b[2]), Double.parseDouble(b[3]));
        this.hull = new Poly(parse(hullString));
    }

    private Point[] parse(String hullString) {
        String[] parts = hullString.split(";");
        Point[] result = new Point[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String[] p = parts[i].split(",");
            result[i] = new Point(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
        }
        return result;
    }

    /**
     * Sorts by area covered, largest first
     *
     * @param o other file
     * @return -1, 0, 1 depending on sort order
     */
    public int compareTo(GeoFile o) {
        return Double.compare(o.bounds.area(), bounds.area());
    }

    /**
     * Returns true if the point is likely to be covered
     * This is not exact as it uses the convex hull, and many feature files are not very convex
     *
     * @param p test point
     * @return true if it is likely to be inside. False means it definitely is not
     */
    public boolean covers(Point p) {
        // Do the bounds check first for speed
        return bounds.contains(p) && hull.contains(p);
    }

    public String toString() {
        return name;
    }

}
