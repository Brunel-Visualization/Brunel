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
 * Standard Mercator Projection
 */

class Mercator extends Projection {
    public String d3Definition(Rect bounds) {
        Rect ext = transform(bounds);

        // We find the center in projected space, and then invert the projection
        Point c = inverse(ext.center());
        String center = ".center([" + F.format(c.x) + ", " + F.format(c.y) + "])";

        return "d3.geoMercator()"
                + LN + translateDefinition()
                + LN + scaleDefinition(ext)
                + LN + center;
    }

    public Point transform(Point p) {
        double a = Math.toRadians(p.x);
        double lon = Math.min(89.5, Math.max(p.y, -89.5));
        double b = Math.toRadians(lon);
        return new Point(a, Math.log(Math.tan(Math.PI / 4 + b / 2)));
    }

    public Point inverse(Point p) {
        double a = Math.toDegrees(p.x);
        double b = Math.toDegrees(2 * Math.atan(Math.exp(p.y)) - Math.PI / 2);
        return new Point(a, b);
    }

}
