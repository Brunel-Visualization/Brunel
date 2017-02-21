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

package org.brunel.build.util;

import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.Accessibility;
import org.brunel.build.util.ScriptWriter;

/**
 * This contains methods for modifying an SVG group; a container for other items
 */
public class SVGGroupUtility {
    public final String className;                  // ID for the chart group
    private final ChartStructure structure;         // General info
    private final ScriptWriter out;                 // Where to write info to

    public SVGGroupUtility(ChartStructure structure, String className, ScriptWriter out) {
        this.className = className;                    // The class for our group
        this.structure = structure;
        this.out = out;
    }

    /**
     * Writes out accessible labels for the group
     */
    public void addAccessibleChartInfo() {
        if (structure.accessible)
            addAccessibleTitle(Accessibility.makeNumberingTitle("chart", structure.chartIndex));
    }

    /**
     * Writes out accessible labels for the group
     *
     * @param title the name to give this
     */
    public void addAccessibleTitle(String title) {
        if (structure.accessible)
            Accessibility.writeLabelAttribute(title, out);
    }

    public void addClipPathReference(String part) {
        out.addChained("attr('clip-path', 'url(#" + clipID(part) + ")')");
    }

    /**
     * Returns a string that defines a chart group
     *
     * @return Javascript fragment that appends a group
     */
    public String createChart() {
        return "vis.append('g').attr('class', '" + className + "')";
    }

    public void defineInnerClipPath() {
        // Make the clip path for this: we expand by a pixel to avoid ugly cut-offs right at the edge
        out.add("vis.append('clipPath').attr('id', '" + clipID("inner") + "').append('rect')")
                .addChained("attr('x', 0).attr('y', 0)")
                .addChained("attr('width', geom.inner_rawWidth+1).attr('height', geom.inner_rawHeight+1)")
                .endStatement();
    }

    public void defineHorizontalAxisClipPath() {
        // we add a cut-out for the Y axis
        out.add("vis.append('clipPath').attr('id', '" + clipID("haxis") + "').append('polyline')")
                .addChained("attr('points', '-1,-1000, -1,-1 -5,5, -1000,5, -100,1000, 10000,1000 10000,-1000')")
                .endStatement();
    }

    public void defineVerticalAxisClipPath() {
        // we add a cut-out for the Y axis
        out.add("vis.append('clipPath').attr('id', '" + clipID("vaxis") + "').append('polyline')")
                .addChained("attr('points', '-1000,-10000, 10000,-10000, "
                        + "10000,' + (geom.inner_rawHeight+1) + ', "
                        + "-1,' + (geom.inner_rawHeight+1) + ', "
                        + "-1,' + (geom.inner_rawHeight+5) + ', "
                        + "-1000,' + (geom.inner_rawHeight+5) )")
                        .endStatement();
    }

    // returns an id that is unique to the chart and the visualization
    private String clipID(String part) {
        return "clip_" + structure.visIdentifier
                + "_chart" + (structure.chartIndex+1)
                + "_" + part;
    }

}
