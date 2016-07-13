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

package org.brunel.build.d3;

import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.Accessibility;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;

/**
 * This contains methods for modifying an SVG group; a container for other items
 */
public class SVGGroupUtility {
    public final String chartClassID;               // ID for the chart group
    private final ChartStructure structure;
    private final BuilderOptions options;

    public SVGGroupUtility(ChartStructure structure, BuilderOptions options) {
        this.chartClassID = "chart" + structure.chartID();                    // The class for our group
        this.structure = structure;
        this.options = options;
    }

    /**
     * Writes out accessible labels for the group
     *
     * @param out where to write to
     */
    public void addAccessibleChartInfo(ScriptWriter out) {
        if (!options.accessibleContent) return;
        String label = Accessibility.makeNumberingTitle("chart", structure.chartIndex);
        Accessibility.writeLabelAttribute(structure, out, label);
    }

    public void addClipPathReference(ScriptWriter out) {
        out.addChained("attr('clip-path', 'url(#" + clipID("clip_") + ")')");
    }

    /**
     * Returns a string that defines a chart group
     *
     * @return Javascript fragment that appends a group
     */
    public String createChart() {
        return "vis.append('g').attr('class', 'chart" + structure.chartID() + "')";
    }

    public void defineClipPath(ScriptWriter out) {
        // Make the clip path for this: we expand by a pixel to avoid ugly cut-offs right at the edge
        out.add("vis.append('clipPath').attr('id', '" + clipID("clip_") + "').append('rect')");
        out.addChained("attr('x', -1).attr('y', -1)");
        out.addChained("attr('width', geom.inner_rawWidth+2).attr('height', geom.inner_rawHeight+2)").endStatement();
    }

    // returns an id that is unique to the chart and the visualization
    private String clipID(String prefix) {
        return prefix + options.visIdentifier + "_" + structure.chartID();
    }

}
