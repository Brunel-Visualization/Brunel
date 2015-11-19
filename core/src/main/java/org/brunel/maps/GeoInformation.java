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

import org.brunel.build.util.PositionFields;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.geom.Rect;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains all the information needed to synchronize and build visualizations based on maps
 */
public class GeoInformation {

    public static String getIDField(VisSingle vis) {
        if (vis.fKeys.isEmpty()) {
            if (vis.positionFields().length == 0)
                return null;
            return vis.positionFields()[0];
        } else {
            return vis.fKeys.get(0).asField();
        }
    }

    private final GeoMapping[] geo;                                 // Geographic mappings, one per element
    private final VisSingle[] element;
    private final PositionFields positionFields;
    private final PointCollection points;
    private final Rect bounds;                                      // Combined bounds
    private final GeoAnalysis analysis = GeoAnalysis.instance();

    public GeoInformation(VisSingle[] elements, Dataset[] elementData, PositionFields positionFields) {
        this.element = elements;
        this.positionFields = positionFields;
        this.points = getAllPoints(elements, positionFields);
        geo = makeGeoMappings(element, elementData);

        Rect bounds = points.bounds();
        for (GeoMapping g : geo)
            if (g != null && g.totalBounds() != null)
                bounds = g.totalBounds().union(bounds);
        this.bounds = bounds;
    }

    public PointCollection getAllPoints(VisSingle[] elements, PositionFields positionFields) {
        PointCollection collection = new PointCollection();
        // Add points for all the fields for each element
        for (VisSingle v : elements) {
            Field[] xx = positionFields.getX(v);
            Field[] yy = positionFields.getY(v);
            for (Field x : xx)
                for (Field y : yy) {
                    for (int i = 0; i < x.rowCount(); i++) {
                        Double a = Data.asNumeric(x.value(i));
                        Double b = Data.asNumeric(y.value(i));
                        if (a != null && b != null) collection.add(a, b);
                    }
                }
        }
        return collection;
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

    public Rect bounds() {
        return bounds;
    }

    // The whole array returned will be null if nothing is a map
    private GeoMapping[] makeGeoMappings(VisSingle[] element, Dataset[] elementData) {
        GeoMapping[] maps = null;
        boolean oneValid = false;
        for (int i = 0; i < element.length; i++) {
            if (element[i].tDiagram == VisTypes.Diagram.map) {
                if (maps == null) maps = new GeoMapping[element.length];
                maps[i] = makeMapping(element[i], elementData[i], positionFields);
                if (maps[i] != null && maps[i].totalBounds() != null) oneValid = true;
            }
        }

        if (!oneValid) {
            // We were unable to create a valid map -- nothing provided location information.
            // We will build a world map. This is an edge case, but supports the simple Brunel 'map'
            for (int i = 0; i < element.length; i++) {
                if (element[i].tDiagram == VisTypes.Diagram.map && element[i].tDiagramParameters.length == 0)
                    maps[i] = analysis.world();
            }
        }

        return maps;
    }

    public GeoMapping makeMapping(VisSingle vis, Dataset data, PositionFields positions) {
        String idField = getIDField(vis);
        if (idField != null)
            return analysis.make(data.field(idField).categories(), vis.tDiagramParameters);
        if (points.isEmpty() || vis.tDiagramParameters != null)
            return analysis.makeForPoints(points, vis.tDiagramParameters);
        return null;
    }

}
