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

package org.brunel.build.chart;

import org.brunel.build.element.ElementStructure;
import org.brunel.data.Dataset;
import org.brunel.maps.GeoInformation;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Manages dependency between elements
 */
public class ChartStructure {

    public final int sourceIndex;

    public final ChartCoordinates coordinates;
    public final int chartIndex;
    public final VisSingle[] elements;
    public final GeoInformation geo;
    public final VisTypes.Diagram diagram;
    public final ElementStructure[] elementStructure;

    private final Dataset[] baseDataSets;

    public ChartStructure(int chartIndex, VisSingle[] elements, Dataset[] data, Dataset[] dataSets) {
        this.baseDataSets = dataSets;
        this.chartIndex = chartIndex;
        this.elements = elements;
        this.coordinates = new ChartCoordinates(elements, data);
        this.elementStructure = new ElementStructure[elements.length];
        this.diagram = findDiagram();
        this.sourceIndex = findSourceElement(elements);

        this.geo = makeGeo(elements, data);

        for (int i = 0; i < elements.length; i++) {
            VisSingle vis = elements[i];
            boolean isDependent = sourceIndex >= 0 && vis.tDiagram == null && vis.positionFields().length == 0;
            GeoMapping geoMapping = geo == null ? null : geo.getGeo(vis);
            elementStructure[i] = new ElementStructure(this, i, vis, data[i], geoMapping, isDependent);
        }

    }

    private VisTypes.Diagram findDiagram() {
        // Any diagram make the chart all diagram. Mixing diagrams and non-diagrams will
        // likely be useless at best, but we will not throw an error for it
        for (VisSingle e : elements) if (e.tDiagram != null) return e.tDiagram;
        return null;
    }

    private int findSourceElement(VisSingle[] elements) {
        int candidate = -1;
        for (int i = 0; i < elements.length; i++) {
            VisSingle vis = elements[i];
            if (vis.fKeys.size() == 1) {
                // A source must have one key only
                if (candidate < 0) {
                    candidate = i;
                } else {
                    // If there are multiple elements with one key, we need to pick the better one
                    // Diagrams are always better sources, otherwise the one that defines positions is better.
                    if (vis.tDiagram != null) return i;
                    if (vis.positionFields().length > elements[candidate].positionFields().length) {
                        candidate = i;
                    }
                }
            }
        }
        return candidate;
    }

    public GeoInformation makeGeo(VisSingle[] elements, Dataset[] data) {
        // If any element specifies a map, we make the map information for all to share
        for (VisSingle vis : elements)
            if (vis.tDiagram == VisTypes.Diagram.map)
                return new GeoInformation(elements, data, coordinates);
        return null;
    }

    public Integer[] elementBuildOrder() {
        // Start with the default order
        Integer[] order = new Integer[elements.length];
        for (int i = 0; i < order.length; i++) order[i] = i;

        Arrays.sort(order, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                VisSingle aa = elements[a], bb = elements[b];

                // Diagrams go first
                if (aa.tDiagram != null && bb.tDiagram == null) return -1;
                if (aa.tDiagram == null && bb.tDiagram != null) return 1;

                // Edges go last
                if (aa.tElement == VisTypes.Element.edge && bb.tElement != VisTypes.Element.edge) return 1;
                if (aa.tElement != VisTypes.Element.edge && bb.tElement == VisTypes.Element.edge) return -1;

                // Otherwise the more keys you have, the later you are built
                return aa.fKeys.size() - bb.fKeys.size();
            }
        });

        return order;
    }

    public int getBaseDatasetIndex(Dataset dataset) {
        for (int i = 0; i < baseDataSets.length; i++)
            if (dataset == baseDataSets[i]) return i;
        throw new IllegalStateException("Could not find data set in array of datasets");
    }

    public String getChartID() {
        return "chart" + chartIndex;
    }

    public ElementStructure getEdge() {
        for (ElementStructure e : elementStructure) if (e.isGraphEdge()) return e;
        throw new IllegalStateException("Networks were requested, but no suitable edge elements were defined");
    }

}
