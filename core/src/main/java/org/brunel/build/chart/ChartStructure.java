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

import org.brunel.build.d3.D3Util;
import org.brunel.build.element.ElementStructure;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.maps.GeoInformation;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages dependency between elements
 */
public class ChartStructure {

    public final Dataset[] baseDataSets;

    public  GeoInformation makeGeo() {
        // If any element specifies a map, we make the map information for all to share
        for (VisSingle e : elements)
            if (e.tDiagram == VisTypes.Diagram.map)
                return new GeoInformation(elements, data, coordinates);
        return null;
    }

    public final int sourceIndex;
    public final ChartCoordinates coordinates;
    public final VisSingle[] elements;
    public final Dataset[] data;
    public final GeoInformation geo;
    public final ElementStructure[] elementStructure;
    private final Set<VisSingle> linked = new LinkedHashSet<VisSingle>();

    public ChartStructure(VisItem chart, VisSingle[]elements, Dataset[] data) {
        this.elements = elements;
        this.data = data;
        this.coordinates = new ChartCoordinates(elements, data);
        this.elementStructure = new ElementStructure[elements.length];
        this.geo = makeGeo();
        this.baseDataSets = chart.getDataSets();

        int src = -1;
        for (int i = 0; i < elements.length; i++) {
            VisSingle v = elements[i];
            elementStructure[i] = new ElementStructure(this, i, v, data[i]);
            if (v.fKeys.isEmpty()) continue;                // Must have keys to be involved
            if (v.positionFields().length > 0 || v.tDiagram != null) {
                if (src < 0) src = i;                       // Defines positions so it is the source (first one wins)
            } else {
                linked.add(v);                              // Does not define positions -- dependent
            }
        }

        if (src < 0) linked.clear();                        // Must have a source for anything to link to
        sourceIndex = src;
    }

    /**
     * Find an element suitable for use as an edge element linking nodes
     *
     * @return VisSingle, or null if it does not exist
     */
    public VisSingle getEdgeElement() {
        for (VisSingle v : linked)
            if (v.fKeys.size() == 2) return v;
        return null;
    }

    public boolean isDependent(VisSingle vis) {
        return linked.contains(vis);
    }

    public boolean isDependent(int i) {
        return linked.contains(elements[i]);
    }

    /**
     * Returns true if this element is defined by a node-edge graph, and this is the edge element
     *
     * @param vis target visualization
     * @return true if we can use graph layout links for this element's position
     */
    public boolean isEdge(VisSingle vis) {
        return getEdgeElement() == vis && elements[sourceIndex].tDiagram == VisTypes.Diagram.network;
    }

    /**
     * Create the Javascript that gives us the required location on a given dimension in data units
     *
     * @param dimName "x" or "y"
     * @param key     field to use for a key
     * @return javascript fragment
     */
    public String keyedLocation(String dimName, Field key) {
        String idToPointName = "elements[" + sourceIndex + "].internal()._idToPoint(";
        return idToPointName + D3Util.writeCall(key) + ")." + dimName;
    }

    public Integer[] elementBuildOrder() {
        // Start with the default order
        Integer[] order = new Integer[elements.length];
        for (int i = 0; i < order.length; i++) order[i] = i;

        // Sort so elements with most keys go last
        // This works at least for nodes and links at least; when we add new cases, we'll need a more complex function
        Arrays.sort(order, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return elements[a].fKeys.size() - elements[b].fKeys.size();
            }
        });

        return order;
    }

    public VisSingle sourceElement() {
        return sourceIndex < 0 ? null : elements[sourceIndex];
    }

    /**
     * Create the Javascript that access the linked data
     *
     * @param other the target VisItem to reference
     */
    public String linkedDataReference(VisSingle other) {
        for (int i = 0; i < elements.length; i++)
            if (elements[i] == other)
                return "elements[" + i + "].data()";
        throw new IllegalStateException("Could not find other VisSingle element");
    }
}
