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
import org.brunel.model.VisTypes;
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

    private static final boolean[] ZOOM_ALL = new boolean[]{true, true};
    private static final boolean[] ZOOM_NONE = new boolean[]{false, false};

    private final ChartStructure structure;     // Chart Structure
    private final D3ScaleBuilder scales;        // Scales for the chart
    private final ScriptWriter out;             // Write definitions here
    private final boolean canZoomX, canZoomY;   // for coordinate zoom, which axes we can zoom

    public D3Interaction(ChartStructure structure, D3ScaleBuilder scales, ScriptWriter out) {
        this.structure = structure;
        this.scales = scales;
        this.out = out;

        boolean[] zoomTypes = zoomRequested(structure.elementStructure);    // user zoom requests
        if (zoomTypes == null) zoomTypes = defaultZooms();                  // defaults if no user request

        // Set the values
        canZoomX = zoomTypes[0];
        canZoomY = zoomTypes[1];

    }

    private boolean[] defaultZooms() {
        // Handle cases when there are diagrams -- the ones that do not fill the space by default are zoomable
        if (structure.diagram == VisTypes.Diagram.network || structure.diagram == VisTypes.Diagram.map
                || structure.diagram == VisTypes.Diagram.tree) return ZOOM_ALL;
        else if (structure.diagram != null)
            return ZOOM_NONE;

        // we cannot zoom diagrams polar coordinates
        if (scales.coords == Coordinates.polar) return ZOOM_NONE;

        // Otherwise allow zoom for non-categorical axes
        return new boolean[]{!structure.coordinates.xCategorical, !structure.coordinates.yCategorical};
    }

    /**
     * We will always write the zoom function in, as it can be used even if the user did not request
     * it to be on available interactively. This way it can be called by API.
     */
    public void defineChartZoomFunction() {
        out.onNewLine().add("zoom: function(params, time) {").indentMore().indentMore().onNewLine()
                .add("if (params) zoom.on('zoom').call(zoomNode, params, time)").endStatement()
                .add("return d3.zoomTransform(zoomNode)").endStatement();
        out.indentLess().indentLess().onNewLine().add("},");
    }

    // Return an array stating whether x and y requested or banned
    // null means no info -- use auto
    private boolean[] zoomRequested(ElementStructure[] elements) {
        // Explicit requests in the code are honored. In case of multiple specs, just the first one is used
        for (ElementStructure e : elements) {
            // "None" means we don't get any interaction
            if (getInteractionParam(e.vis, Interaction.none) != null) return ZOOM_NONE;

            // Find the panzoom request and use it
            Param param = getInteractionParam(e.vis, Interaction.panzoom);
            if (param != null) {
                String s = param.hasModifiers() ? param.firstModifier().asString() : "both";
                if (s.equals("none")) return ZOOM_NONE;
                if (s.equals("x")) return new boolean[]{true, false};
                if (s.equals("y")) return new boolean[]{false, true};
                return ZOOM_ALL;
            }
        }
        return null;
    }

    /**
     * Returns true if the element has any mouse events to be attached to it
     *
     * @param structure the element information
     * @return true if any handler will need to be attached to the element
     */
    public boolean hasElementInteraction(ElementStructure structure) {
        if (!structure.vis.itemsTooltip.isEmpty()) return true;                 // tooltips require a handler
        if (structure.chart.diagram == VisTypes.Diagram.network) return true;   // networks are draggable

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
        if (param == null) return false;                            // No interaction => no targeting
        for (Param p : param.modifiers())
            if (p.asString().startsWith("snap")) return false;      // snap means the handler is attached to background
        return true;                                                // No snap means we do need an element handler
    }

    public static Param getInteractionParam(VisSingle vis, Interaction type) {
        for (Param p : vis.tInteraction)
            if (p.asEnum(Interaction.class) == type) return p;
        return null;
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
                e.add(0, "var c = BrunelD3.closest(merged, '" + snapInfo[0] + "', " + snapInfo[1] + " )");
            }

            out.add("chart.select('rect.overlay')").at(60).comment("Attach handlers to the overlay");
            addDispatchers(overlayEvents);
        }

        if (!elementEvents.isEmpty()) {
            out.add("merged").at(60).comment("Attach handlers to the element");
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
        out.add("var overlay = chart.append('g').attr('class', 'element')")
                .addChained("attr('class', 'overlay').style('cursor','move').style('fill','none').style('pointer-events','all')")
                .endStatement();

        // Add an overlay rectangle for zooming that will trap all mouse events and use them for pan/zoom
        out.add("var zoom = d3.zoom().scaleExtent([1/3,3])").endStatement();

        // Add the zoom overlay and attach behavior
        out.add("var zoomNode = overlay.append('rect').attr('class', 'overlay')")
                .addChained("attr('width', geom.inner_rawWidth)")
                .addChained("attr('height', geom.inner_rawHeight)");

        // Only attach zoom handlers if we want interactivity; otherwise zoom is only available by API
        if (hasZoomInteractivity()) out.addChained("call(zoom)");

        out.addChained("node()").endStatement();
        out.add("zoomNode.__zoom = d3.zoomIdentity").endStatement();
    }

    public boolean hasZoomInteractivity() {
        return canZoomX || canZoomY;
    }

    /**
     * Set up the overlay group and shapes for trapping events for zooming.
     */
    public void addZoomFunctionality() {

        // Define the zoom function
        out.add("zoom.on('zoom', function(t, time) {")
                .indentMore().indentMore().onNewLine();

        // If the transform is undefined, define it (and restrict the pan amount for coord charts)
        if (structure.diagram == null)
            out.add("t = t ||BrunelD3.restrictZoom(d3.event.transform, geom, this)");
        else
            out.add("t = t || d3.event.transform");

        out.endStatement();

        // Only the map has no scales to modify ...
        if (structure.diagram != VisTypes.Diagram.map) {
            if (canZoomX) applyZoomToScale(0);
            if (canZoomY) applyZoomToScale(1);
        }

        out.add("zoomNode.__zoom = t").endStatement();

        if (structure.diagram == VisTypes.Diagram.network) {
            // A network has a defined simulation, which we need to prod if not running
            out.comment("If the simulation has stopped, run one pass to use the scale");
            out.add("if (simulation && simulation.alpha() < simulation.alphaMin()) simulation.on('tick')()").endStatement();
        } else if (structure.diagram == VisTypes.Diagram.cloud) {
            // A cloud just gets the container transformed
            out.add("interior.attr('transform', d3.zoomTransform(zoomNode))").endStatement();
        } else {
            // rebuild the chart
            out.add("build(time || -1)").endStatement();
        }
        out.indentLess().indentLess().add("})").endStatement();

    }

    /**
     * Apply the zoom transform to the scale.
     * This code is called during the handling of a zoom event (the transform is in the variable 't')
     *
     * @param dimension 0 for X, 1 for Y
     */
    private void applyZoomToScale(int dimension) {
        // Which is the screen dimension for this scale dimension?
        boolean isScreenX = structure.coordinates.isTransposed() ? dimension == 1 : dimension == 0;
        String offset = isScreenX ? "t.x" : "t.y";

        out.add(dimension == 0 ? "scale_x" : "scale_y");

        boolean xCategorical = structure.diagram == null && structure.coordinates.xCategorical;
        boolean yCategorical = structure.diagram == null && structure.coordinates.yCategorical;

        if (dimension == 0 && xCategorical) {
            // We cannot change the domain, so we change the range instead, which we know runs from 0 to the geom extent
            out.add(".range([" + offset + ", " + offset + " + t.k * geom.inner_width])");
        } else if (dimension == 1 && yCategorical) {
            // We cannot change the domain, so we change the range instead, which we know runs from 0 to the geom extent
            out.add(".range([" + offset + ", " + offset + " + + t.k * geom.inner_height])");
        } else {
            // D3 allows us to manipulate the domain of the NUMERIC scale using the transform's rescale method
            // We rescale the stored original untransformed scale 'baseScales[dimension]'
            out.add(" =", isScreenX ? "t.rescaleX" : "t.rescaleY");
            out.add("(base_scales[" + dimension + "])");
        }

        out.endStatement();
    }

}
