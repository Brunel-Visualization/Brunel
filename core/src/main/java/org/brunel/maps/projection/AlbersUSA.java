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

import org.brunel.geom.Rect;

/**
 * This is not the real Albers USA projection, as we do not really need the piecing together.
 * Instead it just does a regular Albers for the USA, and generates better code for D3
 */
class AlbersUSA extends Albers {

    public AlbersUSA() {
        super(29.5, 45.5, 96);
    }

    public String d3Definition(Rect bounds) {
        // Everything is well known, so easy to define with fixed details
        return "d3.geo.albersUsa()"
                + LN + ".scale(Math.min(" + width + "/0.96, " + height + "/0.48))"
                + LN + translateDefinition();
    }

    public Rect projectedBounds() {
        return transform(new Rect(-125, -65, 25, 50));
    }
}
