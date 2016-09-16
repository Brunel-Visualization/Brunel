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

import org.brunel.action.Param;
import org.brunel.build.info.ChartCoordinates;
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
import org.brunel.model.VisTypes.Diagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class contains all the information needed to synchronize and build visualizations based on maps
 */
public class GeoInformation {

    private static final String KEY_GEO_NAMES = "geo names";

    public static String getIDField(VisSingle vis) {
        if (vis.fKeys.isEmpty()) {
            if (vis.positionFields().length == 0)
                return null;
            return vis.positionFields()[0];
        } else {
            return vis.fKeys.get(0).asField();
        }
    }

    /**
     * The fraction of values that are suitable geographic names
     *
     * @param f field to analyze
     * @return fraction in the range zero to one
     */
    public static double fractionGeoNames(Field f) {
        if (f.property(KEY_GEO_NAMES) == null) {
            // Calculate suitability for use as a geographic name
            double count = 0;
            if (!f.isNumeric()) {
                HashSet<Object> unmatched = new HashSet<>();
                GeoData.instance().mapFeaturesToFiles(f.categories(), unmatched);
                for (int i = 0; i < f.rowCount(); i++)
                    if (!unmatched.contains(f.value(i))) count++;
            }
            f.set(KEY_GEO_NAMES, count / f.valid());
        }
        return f.numProperty(KEY_GEO_NAMES);
    }

    public List<LabelPoint> getLabelsForFiles() {

        // USe a set to ensure points are not duplicated
        Set<LabelPoint> points = new HashSet<>();

        for (GeoMapping g : geo.values()) {
            for (GeoFile f : g.files) {
                for (LabelPoint p : f.pts) if (hull.bounds.contains(p)) {
                    points.add(p);
                }
            }
        }

        List<LabelPoint> results = new ArrayList<>(points);
        Collections.sort(results);
        return results;
    }

    private final Map<VisSingle, GeoMapping> geo;            // Geographic mappings, one per geo element
    private final VisSingle[] elements;
    private final ChartCoordinates positionFields;
    private final Poly hull;                                        // Convex hull for the points
    private final boolean needsExpansion;
    private final Projection projection;
    public final boolean withGraticule;

    public GeoInformation(VisSingle[] elements, Dataset[] datas, ChartCoordinates positionFields) {
        this.elements = elements;
        this.positionFields = positionFields;
        Poly positionHull = getPositionPoints(positionFields);
        this.geo = makeGeoMappings(datas, positionHull, this);
        this.withGraticule = includesAxes(elements);
        Poly withoutReferenceMaps = combineForHull(positionHull, geo, false);
        if (withoutReferenceMaps.count() == 0) {
            this.hull = combineForHull(positionHull, geo, true);
            this.needsExpansion = false;
        } else {
            this.hull = withoutReferenceMaps;
            this.needsExpansion = true;
        }

        this.projection = ProjectionBuilder.makeProjection(getProjectionBounds());
    }

    private boolean includesAxes(VisSingle[] elements) {
        // Run through all the axes defs for all the elements, if anything says none, no axes.
        // Otherwise anything else means "yes"
        boolean required = false;
        for (VisSingle vis : elements)
            for (VisTypes.Axes e : vis.fAxes.keySet()) {
                if (e == VisTypes.Axes.none) return false;
                required = true;
            }

        return required;
    }

    private Rect getProjectionBounds() {






        Rect hawaii = new Rect(-181, -154, 18, 30);
        Rect alaskanIslandsA = new Rect(-181, -165, 50, 55);
        Rect alaskanIslandsB = new Rect(170, 181, 50, 55);

        Rect bounds = hull.bounds;

        if (positionFields.xExtent != null)
            bounds = new Rect(positionFields.xExtent[0], positionFields.xExtent[1], bounds.top, bounds.bottom);
        if (positionFields.yExtent != null)
            bounds = new Rect(bounds.left, bounds.right, positionFields.yExtent[0], positionFields.yExtent[1]);

        // Do the points wrap around the globe, but only because of alaskan islands?
        if (bounds.left < -179 && bounds.right > 179) {
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            for (Point p : hull.points) {
                // Skip points in areas we do not want to consider for the projection
                if (hawaii.contains(p) || alaskanIslandsA.contains(p) || alaskanIslandsB.contains(p)) continue;
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
            }
            if (maxX > minX) return new Rect(minX, maxX, bounds.top, bounds.bottom);
        }
        return bounds;
    }

    private Poly combineForHull(Poly pointsHull, Map<VisSingle, GeoMapping> geo, boolean includeReferenceMaps) {
        List<Point> combined = new ArrayList<>();
        Collections.addAll(combined, pointsHull.points);

        // Add in the hulls for each of the contained files
        for (GeoMapping g : geo.values())
            if (g != null && (includeReferenceMaps || !g.isReference())) {
                for (GeoFile f : g.files)
                    Collections.addAll(combined, f.hull.points);
            }

        // And return the hull of the combined set
        return Geom.makeConvexHull(combined);
    }

    public String d3Definition() {
        Rect rect = getProjectionBounds();
        if (needsExpansion) rect = rect.expand(0.1);
        return projection.d3Definition(rect);
    }

    private Poly getPositionPoints(ChartCoordinates positionFields) {
        Set<Point> points = new HashSet<>();
        // Add points for all the fields for each element
        for (VisSingle e : elements) {
            Field[] xx = positionFields.getX(e);
            Field[] yy = positionFields.getY(e);
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

    /**
     * Return the GeoMapping for the indicated VisSingle
     *
     * @param e target
     * @return result
     */
    public GeoMapping getGeo(VisSingle e) {
        return geo.get(e);
    }

    public Rect projectedBounds() {
        return projection.projectedBounds(hull.points);
    }

    public Rect bounds() {
        return hull.bounds;
    }

    public Point transform(Point p) {
        return projection.transform(p);
    }

    // The whole array returned will be null if nothing is a map
    private Map<VisSingle, GeoMapping> makeGeoMappings(Dataset[] datas, Poly positionHull, GeoInformation geoInfo) {
        Map<VisSingle, GeoMapping> map = new LinkedHashMap<>();
        GeoFile[] validFiles = null;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i].tDiagram == Diagram.map) {
                GeoMapping mapping = makeMapping(elements[i], datas[i], positionHull, geoInfo);
                if (mapping != null) {
                    map.put(elements[i], mapping);
                    if (validFiles == null) validFiles = mapping.files;
                }
            }
        }

        if (validFiles == null) {
            // We were unable to create a valid map -- nothing provided location information.
            // We will build a world map. This is an edge case, but supports the simple Brunel 'map'
            GeoMapping world = world();
            validFiles = world.files;
            for (VisSingle e : elements) {
                if (e.tDiagram == Diagram.map && e.tDiagramParameters.length == 0) {
                    map.put(e, world);
                }
            }
        }

        // Now run through any elements that should have a mapping, but do not
        for (VisSingle e : elements) {
            if (e.tDiagram == Diagram.map && map.get(e) == null) {
                String quality = GeoData.getQuality(e.tDiagramParameters);
                map.put(e, GeoMapping.createGeoMapping(new Object[0], Arrays.asList(validFiles), GeoData.instance(), quality, geoInfo));
            }
        }

        return map;
    }

    private  GeoMapping makeMapping(VisSingle e, Dataset data, Poly positionHull, GeoInformation geoInfo) {
        String idField = getIDField(e);
        Param[] diagramParameters = e.tDiagramParameters;
        if (idField != null)
            return make(data.field(idField).categories(), diagramParameters);
        if (positionHull.count() > 0 || diagramParameters != null)
            return makeForPoints(positionHull, diagramParameters);
        return null;
    }

    /**
     * For a set of features, returns the mapping to use for them
     *
     * @param names feature names
     * @return resulting mapping
     */
    GeoMapping make(Object[] names, Param[] geoParameters) {
        return GeoMapping.createGeoMapping(names, GeoData.instance().makeRequiredFiles(geoParameters), GeoData.instance(), GeoData.getQuality(geoParameters), this);
    }

    GeoMapping world() {
        return make(new Object[0], new Param[]{Param.makeString("world")});
    }

    GeoMapping makeForPoints(Poly hull, Param[] geoParameters) {
        return GeoMapping.createGeoMapping(hull, GeoData.instance().makeRequiredFiles(geoParameters), GeoData.instance(), GeoData.getQuality(geoParameters), this);
    }


}
