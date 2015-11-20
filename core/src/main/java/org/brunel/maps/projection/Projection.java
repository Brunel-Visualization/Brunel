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

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * A map projection
 */
public abstract class Projection {

    public Rect projectedBounds(Point[] points) {
        Rect r = null;
        for (Point p : points) {
            Point q = transform(p);
            if (r==null)
                r = new Rect(q.x, q.x, q.y,q.y);
            else
                r = r.union(q);
        }
        return r;
    }

    protected double asRadians(double lon) {
        return Math.toRadians(lon);
    }

    protected static final String width = "geom.inner_width";
    protected static final String height = "geom.inner_height";
    protected static final String LN = "\n\t\t";                                      // for output formatting
    protected final NumberFormat F = new DecimalFormat("#.####");                     // for output formatting


    protected String translateDefinition() {
        return ".translate([" + width + "/2, " + height + "/2])";
    }

    protected String scaleDefinition(Rect extent) {
        return ".scale(Math.min((" + width + "-4)/" + F.format(extent.width())
                + ", (" + height + "-4)/" + F.format(extent.height()) + "))";
    }

    /**
     * The D3 definition
     *
     * @return D3 string defining the function
     * @param bounds the bounds to display
     */
    public abstract String d3Definition(Rect bounds);

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
    public Rect transform(Rect b) {
        Rect bounds = null;
        for (Point pt : b.makeBoundaryPoints()) {
            Point p = transform(pt);
            if (bounds == null) bounds = new Rect(p.x, p.x, p.y, p.y);
            else bounds = bounds.union(p);
        }
        return bounds;
    }

}
