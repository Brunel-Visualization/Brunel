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

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.VisTypes.Interaction;

import java.util.List;

/**
 * sc
 * Handles adding interactivity to charts
 */
public class D3Interaction {

    public enum ZoomType {
        MapZoom,
        CoordinateZoom,
        None
    }

    private final ChartStructure structure;     // Chart Structure
    private final D3ScaleBuilder scales;        // Scales for the chart
    private final ScriptWriter out;             // Write definitions here
    private final ZoomType zoomable;             // Same for all elements

    public D3Interaction(ChartStructure structure, D3ScaleBuilder scales, ScriptWriter out) {
        this.structure = structure;
        this.scales = scales;
        this.out = out;
        if (isZoomable(structure.elementStructure))
            this.zoomable = structure.geo == null ? ZoomType.CoordinateZoom : ZoomType.MapZoom;
        else this.zoomable = ZoomType.None;
    }

    public ZoomType getZoomType() {
        return zoomable;
    }

    private boolean isZoomable(ElementStructure[] elements) {
        // Check for things that just will not work currently
        if (structure.coordinates.xCategorical && structure.coordinates.yCategorical)
            return false;  // Only zoom numerical
        if ((structure.diagram != null && structure.geo == null) || scales.coords == Coordinates.polar)
            return false;  // Doesn't work

        // If anything says we want it, we get it
        // Otherwise, if anything says we do not, we do not
        // Otherwise, we get it
        boolean defaultResult = true;
        for (ElementStructure e : elements) {
            if (e.vis.tInteraction.containsKey(Interaction.panzoom)) return true;
            if (e.vis.tInteraction.containsKey(Interaction.none)) defaultResult = false;
        }
        return defaultResult;
    }

    /**
     * This attaches event handlers to the element for click-selection
     */
    public void addElementHandlers(VisSingle vis, D3DataBuilder dataBuilder) {
        // Standard event selection
        Param p = vis.tInteraction.get(Interaction.select);
        if (p != null)
            out.add("selection.on('" + eventName(p) + "', function(d) { BrunelD3.select(d.row, processed, original, this, updateAll) } )").endStatement();

        // Custom event selection

        /*
         * This gets called with the following parameters:
         *
         * row -- the row in the processed data (after aggregation, series, summaries, etc.)
         * value -- the value of the key fields for the item hit.
         *          May be an array (for multiple keys) or a comma-separated list of values if the key is #row
         * data -- the processed data object
         * element -- description of the element; in particular element.fields.key gives the key fields for this element
         *
         * When the function is called "this" is assigned to the SVG element that was interacted with
         */

        p = vis.tInteraction.get(Interaction.call);
        if (p != null)
            out.add("selection.on('" + eventName(p) + "', " + functionName(p, dataBuilder) + " )").endStatement();

    }

    // The first parameter name is the event name, which defaults to "click"
    private String eventName(Param p) {
        Param param = p.firstModifier();
        return param == null ? "click" : param.asString();
    }

    // The second parameter name is the function name, which defaults to a call to alert
    private String functionName(Param p, D3DataBuilder dataBuilder) {
        String base;
        if (p.modifiers().length > 1) return p.modifiers()[1].asString();
        else
            base = "BrunelD3.showSelect";

        List<String> list = dataBuilder.makeKeyFields();
        if (list.size() == 1 && list.get(0).equals("#row")) {
            return "function(d) { " + base + ".call(this, d.row, data._key(d.row).toString(), processed, element) }";
        } else if (list.size() == 1) {
            return "function(d) { " + base + ".call(this, d.row, data._key(d.row), processed, element) }";
        } else {
            return "function(d) { " + base + ".call(this, d.row, data._key(d.row).split('|'), processed, element) }";
        }

    }

    /**
     * Set up the overlay group and shapes for trapping events for zooming.
     */
    public void addPrerequisites() {
        if (zoomable == ZoomType.CoordinateZoom) {
            // The group for the overlay
            out.add("var overlay = interior.append('g').attr('class', 'element')")
                    .addChained("attr('class', 'overlay').style('cursor','move').style('fill','none').style('pointer-events','all')")
                    .endStatement();
            // Add an overlay rectangle for zooming that will trap all mouse events and use them for pan/zoom
            out.add("var zoom = d3.behavior.zoom().on('zoom', function() {build(-1)} )").endStatement();

            out.add("overlay.append('rect').attr('class', 'overlay')");
            if (scales.coords == Coordinates.transposed) {
                out.addChained("attr('width', geom.inner_height)")
                        .addChained("attr('height', geom.inner_width)");
            } else {
                out.addChained("attr('width', geom.inner_width)")
                        .addChained("attr('height', geom.inner_height)");
            }
            out.addChained("call(zoom)").endStatement();
        }
    }

    /**
     * Zooming modifies the scales, and this code makes that happen
     */
    public void addScaleInteractivity() {
        if (zoomable == ZoomType.None) return;
        out.add("zoom");
        if (scales.coords == Coordinates.transposed) {
            // Attach x to y and y to x
            if (!structure.coordinates.xCategorical) out.add(".y(scale_x)");
            if (!structure.coordinates.yCategorical) out.add(".x(scale_y)");
        } else {
            if (!structure.coordinates.xCategorical) out.add(".x(scale_x)");
            if (!structure.coordinates.yCategorical) out.add(".y(scale_y)");
        }
        out.endStatement();
    }
}
