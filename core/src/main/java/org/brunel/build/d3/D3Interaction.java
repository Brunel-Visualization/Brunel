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

import java.util.ArrayList;
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
    private final ZoomType zoomable;            // Same for all elements
    private final boolean canZoomX, canZoomY;   // for coordinate zoom, which axes we can zoom

    public D3Interaction(ChartStructure structure, D3ScaleBuilder scales, ScriptWriter out) {
        this.structure = structure;
        this.scales = scales;
        this.out = out;
        if (isZoomable(structure.elementStructure)) {
            if (structure.geo == null) {
                this.zoomable = ZoomType.CoordinateZoom;
                canZoomX = checkCoordinate(structure, "x", structure.coordinates.xCategorical);
                canZoomY = checkCoordinate(structure, "y", structure.coordinates.yCategorical);
            } else {
                this.zoomable = ZoomType.MapZoom;
                canZoomX = canZoomY = true;
            }
        } else {
            this.zoomable = ZoomType.None;
            canZoomX = canZoomY = false;
        }
    }

    /**
     * Decide if this dimension is to be pan/zoomed
     *
     * @param structure        chart structure
     * @param dimName          "x" or "y"
     * @param dimIsCategorical if true, the dimension is categorical
     * @return true if we want to zoom for this dimension
     */
    private boolean checkCoordinate(ChartStructure structure, String dimName, boolean dimIsCategorical) {
        for (VisSingle e : structure.elements) {
            Param param = e.tInteraction.get(Interaction.panzoom);
            if (param != null && param.hasModifiers()) {
                String s = param.firstModifier().asString();
                return dimName.equalsIgnoreCase(s) || "both".equalsIgnoreCase(s);
            }
        }
        return !dimIsCategorical;
    }

    public ZoomType getZoomType() {
        return zoomable;
    }

    private boolean isZoomable(ElementStructure[] elements) {
        // we cannot zoom diagrams (except for maps) or polar coordinates
        if ((structure.diagram != null && structure.geo == null) || scales.coords == Coordinates.polar)
            return false;

        // Explicit requests in the code are honored. In case of multiple specs, just the first one is used
        for (ElementStructure e : elements) {
            Param param = e.vis.tInteraction.get(Interaction.panzoom);
            if (param != null) return isNeeded(param);
            if (e.vis.tInteraction.containsKey(Interaction.none)) return false;
        }

        // By default, we do not zoom for all categorical data
        if (structure.coordinates.xCategorical && structure.coordinates.yCategorical)
            return false;

        // Otherwise, we want some zooming
        return true;
    }

    private boolean isNeeded(Param param) {
        if (!param.hasModifiers()) return true;
        String s = param.firstModifier().asString();
        return !s.equals("none");
    }

    /**
     * This attaches event handlers to the element for click-selection
     */
    public void addElementHandlers(VisSingle vis, D3DataBuilder dataBuilder) {
        // Standard event selection
        Param p = vis.tInteraction.get(Interaction.select);
        String eventName = p == null ? null : eventName(p);
        if (p != null) {
            out.add("selection.on('" + eventName + "', function(d) { BrunelD3.select(d.row, processed, original, this, updateAll) } )").endStatement();
            if (eventName.equals("mouseover"))
                out.add("selection.on('mouseout', function(d) { BrunelD3.select(null, processed, original, this, updateAll) } )").endStatement();
        }

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
        if (p != null) {
            out.add("selection.on('" + eventName + "', " + functionName(p, dataBuilder) + " )").endStatement();
        }

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
            out.add("var zoom = d3.behavior.zoom()").endStatement();
            out.add("zoom.on('zoom', function() {build(-1)} )").endStatement();

            out.add("overlay.append('rect').attr('class', 'overlay')")
                    .addChained("attr('width', geom.inner_rawWidth)")
                    .addChained("attr('height', geom.inner_rawHeight)");
            out.addChained("call(zoom)").endStatement();
        }
    }

    /**
     * Zooming modifies the scales, and this code makes that happen
     */
    public void addScaleInteractivity() {
        if (zoomable == ZoomType.None) return;
        List<String> zoomCalls = new ArrayList<>();

        out.add("zoom");

        if (canZoomX) {
            if (structure.coordinates.xCategorical) {
                zoomCalls.add("scale_x.rangePoints([zoom.translate()[0], zoom.translate()[0] + zoom.scale() * geom.inner_width], 1);");
            } else if (scales.coords == Coordinates.transposed) {
                out.add(".y(scale_x)");
            } else {
                out.add(".x(scale_x)");
            }
        }

        if (canZoomY) {
            if (structure.coordinates.yCategorical) {
                zoomCalls.add("scale_y.rangePoints([zoom.translate()[1], zoom.translate()[1] + zoom.scale() * geom.inner_height], 1);");
            } else if (scales.coords == Coordinates.transposed) {
                out.add(".x(scale_y)");
            } else {
                out.add(".y(scale_y)");
            }
        }

        if (!zoomCalls.isEmpty()) {
            out.addChained("on('zoom', function() {").indentMore();
            for (String s : zoomCalls) out.onNewLine().add(s).endStatement();
            out.onNewLine().add("build(-1)").endStatement();
            out.indentLess().onNewLine().add("})");
        }
        out.endStatement();

    }
}
