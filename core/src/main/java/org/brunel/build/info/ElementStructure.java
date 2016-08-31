/*
 * Copyright (c) 2016 IBM Corporation and others.
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

package org.brunel.build.info;

import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

/**
 * Defines how to display an element
 */
public class ElementStructure {
    public final ChartStructure chart;
    public final int index;
    public final VisSingle vis;
    public final Dataset original;
    public final Dataset data;
    public final GeoMapping geo;
    public final boolean dependent;

    public ElementDetails details;

    public ElementStructure(ChartStructure chartStructure, int elementIndex, VisSingle vis, Dataset data, GeoMapping geo, boolean dependent) {
        this.chart = chartStructure;
        this.index = elementIndex;
        this.vis = vis;
        this.data = data;
        this.geo = geo;
        this.dependent = dependent;
        this.original = vis.getDataset();
    }

    public int getBaseDatasetIndex() {
        return chart.getBaseDatasetIndex(original);
    }

    public String elementID() {
        return "" + (index + 1);
    }

    public boolean isGraphEdge() {
        return (chart.diagram == Diagram.network || chart.diagram == Diagram.tree)
                        && vis.tElement == Element.edge
                        && (vis.fKeys.size() == 2 || vis.positionFields().length == 2);
    }

    /**
     * Create the Javascript that gives us the required location on a given dimension in data units
     *
     * @param dimName "x" or "y"
     * @param key     field to use for a key
     * @return javascript fragment
     */
    public String keyedLocation(String dimName, Field key) {
        String idToPointName = "elements[" + chart.sourceIndex + "].internal()._idToPoint(";
        return idToPointName + D3Util.writeCall(key) + ")." + dimName;
    }

    public String[] makeReferences(Field[] keys) {
        String idToPointName = "elements[" + chart.sourceIndex + "].internal()._idToPoint(";
        String[] references = new String[keys.length];
        for (int i = 0; i < references.length; i++)
            references[i] = idToPointName + D3Util.writeCall(keys[i]) + ")";
        return references;
    }

}
