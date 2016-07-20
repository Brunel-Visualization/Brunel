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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Returns true if the element has any mouse events to be attached to it
     * @return
     * @param structure
     */
    public boolean hasElementInteraction(ElementStructure structure) {
        Map<Interaction, Param> interaction = structure.vis.tInteraction;

        // Snap operates on overlay, not element, so if snap is present, no element handler is needed
        if (interaction.containsKey(Interaction.snap)) return false;

        // Otherwise, check for a handler
        return interaction.containsKey(Interaction.select) || interaction.containsKey(Interaction.call);
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
    public void addHandlers(Map<Interaction, Param> interactions) {

        // Two potential functions for selection
        Param standardSelection = interactions.get(Interaction.select);
        Param customSelection = interactions.get(Interaction.call);

        // Check if we need to do anything
        if (standardSelection == null && customSelection == null) return;

        // Check if we need to snap the selection to the grid. If so find the radius
        // The radius is a String value we can use in an expression
        String snapRadius = findSnapRadius(interactions.get(Interaction.snap));

        if (snapRadius != null) {
            addOverlayHandlers(standardSelection, customSelection, snapRadius);
        } else {
            addElementHandlers(standardSelection, customSelection);
        }
    }

    private void addOverlayHandlers(Param standardSelection, Param customSelection, String snapRadius) {
        LinkedHashMap<String, List<String>> dispatching = new LinkedHashMap<>();

        if (standardSelection != null) {
            // for standard selection, we dispatch to our code.
            // For mouseovers we also add the "mouseout" call to get rid of the selection
            String event = eventName(standardSelection);
            addFunctionDefinition(event, "BrunelD3.select(item, target, element, updateAll)", dispatching);
            if (event.equals("mouseover") || event.equals("mousemove"))
                addFunctionDefinition("mouseout", "BrunelD3.select(null, target, element, updateAll)", dispatching);
        }

        if (customSelection != null) {
            // For mouseovers we also add the "mouseout" call to get rid of the selection
            String event = eventName(customSelection);
            addFunctionDefinition(event, customFunction(customSelection, "item", "target"), dispatching);
            if (event.equals("mouseover") || event.equals("mousemove"))
                addFunctionDefinition("mouseout", customFunction(customSelection, "null", "target"), dispatching);
        }

        // Add the definitions of the needed variables (item, target) at the start of each list
        for (List<String> list : dispatching.values()) {
            list.add(0, "var c = BrunelD3.closest(selection), target = c.target");
            list.add(1, "var item = c.distance < " + snapRadius + "? c.item : null");
        }

        out.add("interior.select('rect.overlay')").at(60).comment("Attach handlers to the overlay");
        addDispatchers(dispatching);
    }

    private void addElementHandlers(Param standardSelection, Param customSelection) {
        LinkedHashMap<String, List<String>> dispatching = new LinkedHashMap<>();

        if (standardSelection != null) {
            // for standard selection, we dispatch to our code.
            // For mouseovers we also add the "mouseout" call to get rid of the selection
            String event = eventName(standardSelection);
            addFunctionDefinition(event, "BrunelD3.select(d, this, element, updateAll)", dispatching);
            if (event.equals("mouseover") || event.equals("mousemove"))
                addFunctionDefinition("mouseout", "BrunelD3.select(null, this, element, updateAll)", dispatching);
        }

        if (customSelection != null) {
            // For mouseovers we also add the "mouseout" call to get rid of the selection
            String event = eventName(customSelection);
            addFunctionDefinition(event, customFunction(customSelection, "d", "this"), dispatching);
            if (event.equals("mouseover") || event.equals("mousemove"))
                addFunctionDefinition("mouseout", customFunction(customSelection, "null", "this"), dispatching);
        }

        out.add("selection").at(60).comment("Attach handlers to the element");
        addDispatchers(dispatching);
    }

    private void addDispatchers(Map<String, List<String>> dispatching) {
        // Add all the chained items
        for (Map.Entry<String, List<String>> e : dispatching.entrySet()) {
            String event = e.getKey();

            out.addChained("on('" + event + "', function(d) {");
            List<String> calls = e.getValue();

            if (calls.size() == 1) {
                // write it inline all one one line
                out.add(calls.get(0), "})");
            } else {
                // One call per line
                out.onNewLine().indentMore().indentMore();
                for (String s : calls)
                    out.onNewLine().add(s).endStatement();
                out.onNewLine().indentLess().add("})").indentLess();
            }
        }
        out.endStatement();
    }

    private void addFunctionDefinition(String eventName, String definition, Map<String, List<String>> dispatching) {
        List<String> list = dispatching.get(eventName);
        if (list == null) {
            list = new ArrayList<>();
            dispatching.put(eventName, list);
        }
        list.add(definition);
    }

    // The distance to use to snap points to locations
    private String findSnapRadius(Param param) {
        if (param == null) return null;                     // Undefined, so no snapping
        if (param.hasModifiers()) {
            double d = param.firstModifier().asDouble();
            return d < 1 ? null : "" + (int) d;
        } else {
            return "geom.inner_radius/4";                   // Default to a quarter the space allowed on screen
        }
    }

    // The first parameter name is the event name, which defaults to "click"
    private String eventName(Param p) {
        Param param = p.firstModifier();
        return param == null ? "click" : param.asString();
    }

    // The second parameter name is the function name, which defaults to a call to alert
    private String customFunction(Param p, String param1, String param2) {
        String base = (p.modifiers().length > 1) ?
                p.modifiers()[1].asString() : "BrunelD3.showSelect";
        return base + "(" + param1 + ", " + param2 + ", element)";
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
