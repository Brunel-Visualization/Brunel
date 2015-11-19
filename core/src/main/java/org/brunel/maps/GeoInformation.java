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

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.diagrams.GeoMap;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains all the information needed to synchronize and build visualizations based on maps
 */
public class GeoInformation {

    private final GeoMapping[] geo;                         // Geographic mappings, one per element
    private final VisSingle[] element;
    private final PositionFields positionFields;

    public String geoProjection;  // Projection we used

    public GeoInformation(VisSingle[] element, Dataset[] elementData, PositionFields positionFields) {
        this.element = element;
        this.positionFields = positionFields;
        geo = makeGeoMappings(element, elementData, positionFields);
    }

    public void writeProjection(D3Interaction interaction, ScriptWriter out) {
        // Calculate the full bounds
        Rect bounds = makePositionBounds(positionFields.allXFields, positionFields.allYFields);
        if (bounds == null) {
            // All we have are reference maps -- so just use them
            for (GeoMapping g : geo)
                if (g != null && g.totalBounds() != null)
                    bounds = g.totalBounds().union(bounds);
        } else {
            bounds = bounds.expand(0.05);
            for (int i = 0; i < geo.length; i++) {
                if (geo[i] == null || geo[i].totalBounds() == null) continue;
                Rect trial = geo[i].totalBounds().union(bounds);

                // Increase bounds if the element had actual data (which we obviously want to show)
                // or it is a reference map, but massively bigger than the target area
                if (containsData(element[i]) || bounds.area() > 0.1 * trial.area())
                    bounds = trial;
            }
        }

        // Write the projection for that
        this.geoProjection = GeoMap.writeProjection(out, bounds);
    }

    private Rect makePositionBounds(Field[] xFields, Field[] yFields) {
        double[] rx = getRange(xFields);
        double[] ry = getRange(yFields);
        return rx == null || ry == null ? null : new Rect(rx[0], rx[1], ry[0], ry[1]);
    }

    public List<GeoMapping> getAllGeo() {
        ArrayList<GeoMapping> geoMappings = new ArrayList<GeoMapping>();
        for (GeoMapping g : geo) if (g != null) geoMappings.add(g);
        return geoMappings;
    }

    /**
     * Return the GeoMapping for the indicated VisSingle
     *
     * @param vis target
     * @return result
     */
    public GeoMapping getGeo(VisSingle vis) {
        for (int i = 0; i < element.length; i++)
            if (element[i] == vis) return geo[i];
        throw new IllegalStateException("Could not find vis in known elements");
    }

    public Rect getGeoBounds() {
        Rect bounds = null;
        for (GeoMapping g : geo)
            if (g != null && g.totalBounds() != null)
                bounds = g.totalBounds().union(bounds);
        return bounds;
    }

    // The whole array returned will be null if nothing is a map
    private GeoMapping[] makeGeoMappings(VisSingle[] element, Dataset[] elementData, PositionFields positionFields) {
        GeoMapping[] maps = null;
        boolean oneValid = false;
        for (int i = 0; i < element.length; i++) {
            if (element[i].tDiagram == VisTypes.Diagram.map) {
                if (maps == null) maps = new GeoMapping[element.length];
                maps[i] = GeoMap.makeMapping(element[i], elementData[i], positionFields);
                if (maps[i] != null && maps[i].totalBounds() != null) oneValid = true;
            }
        }

        if (!oneValid) {
            // We were unable to create a valid map -- nothing provided location information.
            // We will build a world map. This is an edge case, but supports the simple Brunel 'map'
            for (int i = 0; i < element.length; i++) {
                if (element[i].tDiagram == VisTypes.Diagram.map && element[i].tDiagramParameters.length == 0)
                    maps[i] = GeoAnalysis.instance().world();
            }
        }

        return maps;
    }

    private double[] getRange(Field[] xFields) {
        Double min = null, max = null;
        for (Field x : xFields) {
            if (x.isNumeric()) {
                if (min == null) {
                    min = x.min();
                    max = x.max();
                } else {
                    min = Math.min(min, x.min());
                    max = Math.min(max, x.max());
                }
            }
        }
        return min == null ? null : new double[]{min, max};
    }

    private boolean containsData(VisSingle vis) {
        // Positional data or keys are real data; otherwise we are likely a background map
        return vis.positionFields().length > 0 || !vis.fKeys.isEmpty();
    }



}
