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
import org.brunel.geom.Geom;
import org.brunel.geom.Point;
import org.brunel.geom.Poly;
import org.brunel.geom.Rect;
import org.brunel.maps.projection.Projection;
import org.brunel.maps.projection.ProjectionBuilder;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static GeoInformation make(VisSingle[] elements, Dataset[] elementData, PositionFields positionFields) {
        // If any element specifies a map, we make the map information for all to share
        for (VisSingle e : elements)
            if (e.tDiagram == VisTypes.Diagram.map)
                return new GeoInformation(elements, elementData, positionFields);
        return null;
    }

    private final GeoMapping[] geo;                                 // Geographic mappings, one per element
    private final VisSingle[] element;
    private final Poly hull;                                        // Convex hull for the points
    private final Projection projection;
    private final Rect projectedBounds;

    private GeoInformation(VisSingle[] elements, Dataset[] elementData, PositionFields positionFields) {
        this.element = elements;
        Poly positionHull = getPositionPoints(elements, positionFields);
        this.geo = makeGeoMappings(elements, elementData, positionHull);
        this.hull = combineForHull(positionHull, geo);
        this.projection = ProjectionBuilder.makeProjection(hull.bounds);
        this.projectedBounds = projection.projectedBounds(hull.points);
    }

    private Poly combineForHull(Poly pointsHull, GeoMapping[] geo) {
        List<Point> combined = new ArrayList<Point>();
        Collections.addAll(combined, pointsHull.points);

        // Add in the hulls for each of the contained files
        for (GeoMapping g1 : geo)
            if (g1 != null) {
                for (GeoFile f : g1.getFiles())
                    Collections.addAll(combined, f.hull.points);
            }
        // And return the hull of the combined set
        return Geom.makeConvexHull(combined);
    }

    public String d3Definition() {
        return projection.d3Definition(hull.bounds);
    }

    private Poly getPositionPoints(VisSingle[] elements, PositionFields positionFields) {
        Set<Point> points = new HashSet<Point>();
        // Add points for all the fields for each element
        for (VisSingle v : elements) {
            Field[] xx = positionFields.getX(v);
            Field[] yy = positionFields.getY(v);
            for (Field x : xx)
                for (Field y : yy) {
                    for (int i = 0; i < x.rowCount(); i++) {
                        Double a = Data.asNumeric(x.value(i));
                        Double b = Data.asNumeric(y.value(i));
                        if (a != null && b != null) points.add(new Point(a, b));
                    }
                }
        }
        return Geom.makeConvexHull(points);
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

    public Rect projectedBounds() {
        return projectedBounds;
    }

    public Point transform(Point p) {
        return projection.transform(p);
    }

    // The whole array returned will be null if nothing is a map
    private GeoMapping[] makeGeoMappings(VisSingle[] element, Dataset[] elementData, Poly positionHull) {
        GeoMapping[] maps = new GeoMapping[element.length];
        boolean oneValid = false;
        for (int i = 0; i < element.length; i++) {
            if (element[i].tDiagram == VisTypes.Diagram.map) {
                maps[i] = makeMapping(element[i], elementData[i], positionHull);
                if (maps[i] != null && maps[i].totalBounds() != null) oneValid = true;
            }
        }

        if (!oneValid) {
            // We were unable to create a valid map -- nothing provided location information.
            // We will build a world map. This is an edge case, but supports the simple Brunel 'map'
            for (int i = 0; i < element.length; i++) {
                if (element[i].tDiagram == VisTypes.Diagram.map && element[i].tDiagramParameters.length == 0)
                    maps[i] = GeoData.instance().world();
            }
        }

        return maps;
    }

    public static GeoMapping makeMapping(VisSingle vis, Dataset data, Poly positionHull) {
        String idField = getIDField(vis);
        if (idField != null)
            return GeoData.instance().make(data.field(idField).categories(), vis.tDiagramParameters);
        if (positionHull.size() > 0 || vis.tDiagramParameters != null)
            return GeoData.instance().makeForPoints(positionHull, vis.tDiagramParameters);
        return null;
    }

}
