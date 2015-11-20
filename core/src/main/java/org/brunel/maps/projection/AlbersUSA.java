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
 * hand-crafted to look similar to what D3 does
 */
class AlbersUSA extends Projection {

    private final Albers lower48 = new Albers(29.5, 45.5, 96);
    private final Albers alaska = new Albers(0, 50, 120);
    private final Albers hawaii = new Albers(-140, 9, 120);
    private final Albers pr = new Albers(0, 21, 120);

    private final Rect lower48Rect;

    private final Point alaskaTopLeft;
    private final Point hawaiiTopLeft;
    private final Point prTopLeft;


    public AlbersUSA() {
         lower48Rect = lower48.transform(new Rect(-125, -65, 25, 50));
         alaskaTopLeft = alaska.transform(new Point(-180, 73));
         hawaiiTopLeft = hawaii.transform(new Point(-160.5, 22.5));
         prTopLeft = hawaii.transform(new Point(-67.5, 18.5));
    }

    public String d3Definition(Rect bounds) {
        // Everything is well known, so easy to define with fixed details
        return "d3.geo.albersUsa()"
                + LN + ".scale(Math.min(" + width + "/0.96, " + height + "/0.48))"
                + LN + translateDefinition();
    }


    public Point transform(Point p) {
        Point q;
        if (p.y > 50) {
            // Alaska
            q= shift(alaska.transform(p), alaskaTopLeft, 0, 0.75);
        } else if (p.x < -140) {
            // Hawaii
            q= shift(hawaii.transform(p), hawaiiTopLeft, 0.33, 0.8);
        } else if (p.y < 21 ) {
            // Puerto Rico
            q= shift(pr.transform(p), prTopLeft, 0.7, 0.8);
        } else {
            // lower 48
            q= lower48.transform(p);
        }
        return q;
    }

    public Point inverse(Point p) {
        throw new UnsupportedOperationException("Not supported");
    }

    private Point shift(Point point, Point base, double targetX, double targetY) {
        return new Point((point.x - base.x) * targetX * lower48Rect.width() + lower48Rect.x1,
                (point.y - base.y) * targetY * lower48Rect.height() + lower48Rect.y1);
    }
}
