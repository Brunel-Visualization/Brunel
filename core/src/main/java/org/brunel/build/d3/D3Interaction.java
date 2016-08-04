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
        GraphicZoom,
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
        if (isSemanticZoomable(structure.elementStructure)) {
            if (structure.geo == null) {
                this.zoomable = ZoomType.CoordinateZoom;
                canZoomX = checkCoordinate(structure, "x", structure.coordinates.xCategorical);
                canZoomY = checkCoordinate(structure, "y", structure.coordinates.yCategorical);
            } else {
                this.zoomable = ZoomType.MapZoom;
                canZoomX = canZoomY = true;
            }
        } else if (zoomRequested(structure.elementStructure)) {
            this.zoomable = ZoomType.GraphicZoom;
            canZoomX = canZoomY = false;
        } else {
            this.zoomable = ZoomType.None;
            canZoomX = canZoomY = false;
        }
    }

    private boolean zoomRequested(ElementStructure[] elements) {
        // Explicit requests in the code are honored. In case of multiple specs, just the first one is used
        for (ElementStructure e : elements) {
            Param param = getInteractionParam(e.vis, Interaction.panzoom);
            if (param != null) return isNeeded(param);
            if (getInteractionParam(e.vis, Interaction.none) != null) return false;
        }
        return false;
    }

    /**
     * Returns true if the element has any mouse events to be attached to it
     *
     * @param structure the element information
     * @return true if any handler will need to be attached to the element
     */
    public boolean hasElementInteraction(ElementStructure structure) {
        if (!structure.vis.itemsTooltip.isEmpty()) return true;         // tooltips require a handler

        for (Param p : structure.vis.tInteraction) {
            String s = p.asString();
            // Only these types create element event handlers
            if (s.equals(Interaction.select.name()) || s.equals(Interaction.call.name())) {
                if (targetsElement(p)) return true;
            }
        }
        return false;
    }

    private boolean targetsElement(Param param) {
        if (param == null) return false;                    // No interaction => no targeting
        for (Param p : param.modifiers())
            if (p.asString().startsWith("snap")) return false;  // snap means the handler is attached to background
        return true;                                        // No snap means we do need an element handler
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
            Param param = getInteractionParam(e, Interaction.panzoom);
            if (param != null && param.hasModifiers()) {
                String s = param.firstModifier().asString();
                return dimName.equalsIgnoreCase(s) || "both".equalsIgnoreCase(s);
            }
        }
        return !dimIsCategorical;
    }

    public static Param getInteractionParam(VisSingle vis, Interaction type) {
        for (Param p : vis.tInteraction)
            if (p.asEnum(Interaction.class) == type) return p;
        return null;
    }

    public ZoomType getZoomType() {
        return zoomable;
    }

    private boolean isSemanticZoomable(ElementStructure[] elements) {
        // we cannot zoom diagrams (except for maps) or polar coordinates
        if ((structure.diagram != null && structure.geo == null) || scales.coords == Coordinates.polar)
            return false;

        // Explicit requests in the code are honored. In case of multiple specs, just the first one is used
        for (ElementStructure e : elements) {
            Param param = getInteractionParam(e.vis, Interaction.panzoom);
            if (param != null) return isNeeded(param);
            if (getInteractionParam(e.vis, Interaction.none) != null) return false;
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
     *
     * @param interactions the defined interactivity
     */
    public void addHandlers(List<Param> interactions) {

        // A map from the event names to a set of commands to write for each event
        // One map for the element, the other for 'snap' events, which go on the overlay
        LinkedHashMap<String, List<String>> elementEvents = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> overlayEvents = new LinkedHashMap<>();

        String[] snapInfo = null;
        for (Param p : interactions)
            if (snapInfo == null) snapInfo = findSnapInfo(p);

        for (Param p : interactions) {
            Interaction type = p.asEnum(Interaction.class);
            if (type == Interaction.select) {
                // One of select, select:mouseXXX, select:snap, select:snap:ZZ
                if (snapInfo != null) {
                    // We want a snap overlay event that will call select -- all snap events are overlays
                    // Also add corresponding mouse out event
                    addFunctionDefinition("mousemove",
                            "BrunelD3.select(c.item, c.target, element, updateAll)", overlayEvents);
                    addFunctionDefinition("mouseout",
                            "BrunelD3.select(null, c.target, element, updateAll)", overlayEvents);
                } else {
                    // We want an event handler on the element -- Also add corresponding mouse out event
                    String eventName = p.hasModifiers() ? p.firstModifier().asString() : "click";
                    addFunctionDefinition(eventName,
                            "BrunelD3.select(d, this, element, updateAll)", elementEvents);
                    if (eventName.equals("mouseover") || eventName.equals("mousemove"))
                        addFunctionDefinition("mouseout",
                                "BrunelD3.select(null, this, element, updateAll)", elementEvents);
                }
            } else if (type == Interaction.call) {
                // One of call, call:func, call:func:mouseXXX, call:func:snap, call:func:snap:ZZ
                String functionName = p.hasModifiers() ? p.firstModifier().asString() : "BrunelD3.crosshairs";
                if (functionName.isEmpty()) functionName = "BrunelD3.crosshairs";
                if (snapInfo != null) {
                    // We want a snap overlay event that will call a custom function -- all snap events are overlays
                    addFunctionDefinition("mousemove", functionName + "(c.item, c.target, element, '" + snapInfo[0] + "')", overlayEvents);
                    addFunctionDefinition("mouseout", functionName + "(null, c.target, element, '" + snapInfo[0] + "')", overlayEvents);
                } else {
                    // We want an event handler on the element
                    String eventName = p.modifiers().length > 1 ? p.modifiers()[1].toString() : "click";
                    addFunctionDefinition(eventName, functionName + "(d, this, element)", elementEvents);
                    if (eventName.equals("mouseover") || eventName.equals("mousemove"))
                        addFunctionDefinition("mouseout", functionName + "(null, this, element)", elementEvents);
                }
            }
        }

        if (!overlayEvents.isEmpty()) {
            // Start each set of overlay commands with a command to find the closest item
            for (List<String> e : overlayEvents.values()) {
                e.add(0, "var c = BrunelD3.closest(selection, '" + snapInfo[0] + "', " + snapInfo[1] + " )");
            }

            out.add("interior.select('rect.overlay')").at(60).comment("Attach handlers to the overlay");
            addDispatchers(overlayEvents);
        }

        if (!elementEvents.isEmpty()) {
            out.add("selection").at(60).comment("Attach handlers to the element");
            addDispatchers(elementEvents);
        }
    }

    /**
     * Adds event handlers for each event, with a list of things to call.
     *
     * @param dispatching map of event name to list of commands
     */
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
    // returns the snap method (snap, snapX, snapY) and then the distance
    private String[] findSnapInfo(Param param) {
        String snapFunction = null;
        for (Param p : param.modifiers()) {
            if (p.asString().startsWith("snap")) {
                if (p.asString().equalsIgnoreCase("snapx")) snapFunction = "x";
                else if (p.asString().equalsIgnoreCase("snapy")) snapFunction = "y";
                else snapFunction = "xy";
            } else if (snapFunction != null) {
                // Next param is the numeric value
                double d = p.asDouble();
                return d < 1 ? null : new String[]{snapFunction, "" + (int) d};
            }
        }

        if (snapFunction != null)
            return new String[]{snapFunction, "geom.inner_radius/4"};   // Default to a quarter the space allowed on screen
        else
            return null;                                    // No snap
    }

    /**
     * Set up the overlay group and shapes for trapping events for zooming.
     */
    public void addOverlayForZoom() {
        // The group for the overlay
        out.add("var overlay = interior.append('g').attr('class', 'element')")
                .addChained("attr('class', 'overlay').style('cursor','move').style('fill','none').style('pointer-events','all')")
                .endStatement();

        // Add an overlay rectangle for zooming that will trap all mouse events and use them for pan/zoom
        out.add("var zoom = d3.zoom().scaleExtent([1/3,3])").endStatement();

        // Add the zoom overlay and attach behavior
        out.add("overlay.append('rect').attr('class', 'overlay')")
                .addChained("attr('width', geom.inner_rawWidth)")
                .addChained("attr('height', geom.inner_rawHeight)");
        if (zoomable != ZoomType.None) out.addChained("call(zoom)");
        out.endStatement();
    }

    /**
     * Set up the overlay group and shapes for trapping events for zooming.
     */
    public void addZoomFunctionality() {

        // Check if anything to do
        if (zoomable == ZoomType.None) return;

        // Define the zoom function
        out.add("zoom.on('zoom', function() {")
                .indentMore().indentMore().ln();

        // Restrict only for coordinate zooming
        if (zoomable == ZoomType.CoordinateZoom) {
            out.add("var t = BrunelD3.restrictZoom(d3.event.transform, geom, this)").endStatement();
        } else {
            out.add("var t = d3.event.transform").endStatement();
        }

        // Zoom by coordinate or projection
        if (zoomable == ZoomType.CoordinateZoom) {
            if (canZoomX) out.add("scale_x = t.rescaleX(base_scales[0])").endStatement();
            if (canZoomY) out.add("scale_y = t.rescaleY(base_scales[1])").endStatement();
            out.add("build(-1)").endStatement();


        }

        // Zoom the map
        if (zoomable == ZoomType.MapZoom) {
            out.add("base._t = t").endStatement();
            out.add("build(-1)").endStatement();
        }

//
//        // Zoom by graphic transform
//        if (zoomable == ZoomType.GraphicZoom)
//            out.add("zoom.on('zoom', function() { interior.attr('transform', 'translate(' + d3.event.translate + ')' + ' scale(' + d3.event.scale + ')' ) } )").endStatement();

        out.indentLess().indentLess().add("})").endStatement();

    }

}
