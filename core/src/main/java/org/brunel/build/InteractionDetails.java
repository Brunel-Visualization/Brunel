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

package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.info.ChartCoordinates;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisElement;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Interaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.brunel.model.VisTypes.Interaction.call;

/**
 * Handles adding interactivity to charts
 */
public class InteractionDetails {

	private static final boolean[] ZOOM_ALL = new boolean[]{true, true};
	private static final boolean[] ZOOM_NONE = new boolean[]{false, false};
	private static final String DEFAULT_SNAP_DISTANCE = "geom.inner_radius/4";

	public static Param getInteractionParam(VisElement vis, Interaction type) {
		for (Param p : vis.tInteraction)
			if (p.asEnum(Interaction.class) == type) return p;
		return null;
	}

	private final boolean canZoomX, canZoomY;   // for coordinate zoom, which axes we can zoom
	private final boolean usesCollapse;         // true if we have a collapse handler
	private final boolean usesExpand;           // true if we have an expand handler
	private final VisTypes.Diagram diagram;        // The diagram for this chart
	private final ChartCoordinates coordinates;    // Coordinate system for the chart

	public InteractionDetails(VisTypes.Diagram diagram, ChartCoordinates coordinates, VisElement[] elements) {
		this.diagram = diagram;
		this.coordinates = coordinates;

		boolean[] zoomTypes = zoomRequested(elements);    // user zoom requests
		if (zoomTypes == null) zoomTypes = defaultZooms();                  // defaults if no user request

		// Set the values
		canZoomX = zoomTypes[0];
		canZoomY = zoomTypes[1];
		usesExpand = expandRequested(elements);

		usesCollapse = diagram != null && !usesExpand
				&& diagram.isHierarchical && diagram != VisTypes.Diagram.gridded
				&& !banned(elements, Interaction.collapse);

	}

	/**
	 * This attaches event handlers to the element for click-selection
	 */
	public void addHandlers(ElementStructure structure, ScriptWriter out) {

		Collection<Param> interactions = structure.vis.tInteraction;
		if (interactions.isEmpty() && structure.chart.diagram == VisTypes.Diagram.parallel) {
			// If we have not specified any interactivity for a parallel axis line, we use hover selection
			interactions = Collections.singleton(
					Param.makeOption("select").addModifiers(Param.makeOption("mouseover"))
			);
		}

		// A map from the event names to a set of commands to write for each event
		// One map for the element, the other for 'snap' events, which go on the overlay
		LinkedHashMap<String, List<String>> elementEvents = new LinkedHashMap<>();
		LinkedHashMap<String, List<String>> overlayEvents = new LinkedHashMap<>();

		// We can only have one way to define snap information, so we just use the first definition
		String[] snapInfo = null;
		for (Param p : interactions)
			if (snapInfo == null) snapInfo = findSnapInfo(p);

		for (Param p : interactions) {
			Interaction type = p.asEnum(Interaction.class);

			// Is this a snap modifier?
			boolean isSnap = false;
			for (Param mod : p.modifiers()) if (mod.toString().startsWith("snap")) isSnap = true;

			if (type == Interaction.select) {
				// One of select, select:mouseXXX, select:snap, select:snap:ZZ
				if (isSnap) {
					// We want a snap overlay event that will call select -- all snap events are overlays
					// Also add corresponding mouse out event
					addFunctionDefinition("mousemove.snap",
							"BrunelD3.select(c.item, c.target, element, updateAll)", overlayEvents);
					addFunctionDefinition("mouseout.snap",
							"BrunelD3.select(null, c.target, element, updateAll)", overlayEvents);

					if (hasElementInteraction(structure)) {
						// We are adding a handler for the element, which means it will steal the even for the background
						// So we must add that handler to the element also

						addSelectEvent(elementEvents, Param.makeOption("select").addModifiers(Param.makeString("mouseover")));
					}

				} else {
					String eventName = addSelectEvent(elementEvents, p);

					// And we want a click on the main space to select nothing
					if (eventName.equals("click"))
						addFunctionDefinition("click.interact", "BrunelD3.select(null, this, element, updateAll)", overlayEvents);

				}
			} else if (type == call) {
				// One of call, call:func, call:func:mouseXXX, call:func:snap, call:func:snap:ZZ
				String functionName = p.hasModifiers() ? p.firstModifier().asString() : "BrunelD3.crosshairs";
				if (functionName.isEmpty()) functionName = "BrunelD3.crosshairs";
				if (isSnap) {
					// We want a snap overlay event that will call a custom function -- all snap events are overlays
					addFunctionDefinition("mousemove.user", functionName + "(c.item, c.target, element, '" + snapInfo[0] + "')", overlayEvents);
					addFunctionDefinition("mouseout.user", functionName + "(null, c.target, element, '" + snapInfo[0] + "')", overlayEvents);

					if (hasElementInteraction(structure)) {
						// We are adding a handler for the element, which means it will steal the even for the background
						// So we must add that handler to the element also
						Param p1 = Param.makeOption("call").addModifiers(Param.makeOption(functionName), Param.makeOption("mouseover"));
						addEvent(elementEvents, p1, functionName);
					}

				} else {
					// We want an event handler on the element
					String eventName = addEvent(elementEvents, p, functionName);

					// And we want a click on the main space to select nothing
					if (eventName.equals("click"))
						addFunctionDefinition("click.user", functionName + "(null, c.target, element, 'xy')", overlayEvents);

				}
			}
		}

		String buildString = "charts[" + structure.chart.chartIndex + "].build(500)";
		if (usesExpand && !structure.isDependentEdge())
			addFunctionDefinition("dblclick.collapse",
					"if (d3.event.shiftKey || !d.parent) expandState.pop(); else if (d.data.children) expandState.push(d.data.key); " + buildString,
					elementEvents);
		if (usesCollapse && !structure.isDependentEdge())
			addFunctionDefinition("dblclick.collapse",
					"if (d.data.children) {collapseState[d.data.key] = !collapseState[d.data.key]; " + buildString + "} ",
					elementEvents);

		if (!overlayEvents.isEmpty()) {
			// Start each set of overlay commands with a command to find the closest item
			for (List<String> e : overlayEvents.values()) {
				if (snapInfo == null)
					e.add(0, "var c = BrunelD3.closest(merged, 'xy', " + DEFAULT_SNAP_DISTANCE + " )");
				else
					e.add(0, "var c = BrunelD3.closest(merged, '" + snapInfo[0] + "', " + snapInfo[1] + " )");
			}

			out.add("chart.select('rect.overlay')").comment("Attach handlers to the overlay");
			addDispatchers(overlayEvents, out);
		}

		if (!elementEvents.isEmpty()) {
			out.add("merged").comment("Attach handlers to the element");
			addDispatchers(elementEvents, out);
		}
	}

	/**
	 * Set up the overlay group and shapes for trapping events for zooming.
	 *
	 * @param diagram the diagram the chart uses
	 */
	public void addOverlayForZoom(VisTypes.Diagram diagram, ScriptWriter out) {
		// The group for the overlay
		out.add("var overlay = chart.append('g').attr('class', 'element').attr('class', 'overlay')");

		out.endStatement();

		String extent;
		if (diagram == VisTypes.Diagram.map)
			extent = "1/5,5";
		else if (diagram == VisTypes.Diagram.parallel ||
				diagram == VisTypes.Diagram.tree || diagram == VisTypes.Diagram.network) extent = "1/2,10";
		else
			extent = "1/3,3";

		// Add an overlay rectangle for zooming that will trap all mouse events and use them for pan/zoom
		out.add("var zoom = d3.zoom().scaleExtent([" + extent + "])").endStatement();

		// Add the zoom overlay and attach behavior
		out.add("var zoomNode = overlay.append('rect').attr('class', 'overlay')")
				.addChained("attr('x', geom.inner_left).attr('y', geom.inner_top)")
				.addChained("attr('width', geom.inner_rawWidth).attr('height', geom.inner_rawHeight)");

		// Set cursor style for pan/zoom

		// Only attach zoom handlers if we want interactivity; otherwise zoom is only available by API
		if (hasZoomInteractivity()) {
			out.addChained("style('cursor', 'move').call(zoom)");
		} else {
			out.addChained("style('cursor', 'default')");
		}

		out.addChained("node()").endStatement();
		out.add("zoomNode.__zoom = d3.zoomIdentity").endStatement();
	}

	/**
	 * Set up the overlay group and shapes for trapping events for zooming.
	 */
	public void addZoomFunctionality(ScriptWriter out) {

		// Define the zoom function
		out.add("zoom.on('zoom', function(t, time) {")
				.indentMore().indentMore().onNewLine();

		// If the transform is undefined, define it (and restrict the pan amount for coord charts)
		if (diagram == null)
			out.add("t = t ||BrunelD3.restrictZoom(d3.event.transform, geom, this)");
		else
			out.add("t = t || d3.event.transform");

		out.endStatement();

		if (diagram == VisTypes.Diagram.parallel) {           // Very special handling
			out.add("var index = Math.round(d3.mouse(zoomNode)[0] * (parallel.length-1) / geom.inner_width)")
					.endStatement();
			out.add("var p = parallel[index]").endStatement();
			out.add("p._scale = p._scale || p.scale").endStatement();
			out.add("if (p.numeric) p.scale = t.rescaleY(p._scale)").endStatement();
			out.add("else           p.scale.range([t.y, t.y + t.k * geom.inner_height])").endStatement();
		} else if (diagram != VisTypes.Diagram.map) {         // The map has no scales to modify ...
			if (canZoomX) applyZoomToScale(0, out);
			if (canZoomY) applyZoomToScale(1, out);
		}

		out.add("zoomNode.__zoom = t").endStatement();

		// Set the zoom level on the interior
		out.add("interior.attr('class', 'interior ' + BrunelD3.zoomLabel(t.k));").endStatement();

		if (diagram == VisTypes.Diagram.network) {
			// A network has a defined simulation, which we need to prod if not running
			out.comment("If the simulation has stopped, run one pass to use the scale");
			out.add("if (simulation && simulation.alpha() < simulation.alphaMin()) simulation.on('tick')()").endStatement();
		} else if (diagram == VisTypes.Diagram.cloud) {
			// A cloud just gets the container transformed
			out.add("interior.attr('transform', d3.zoomTransform(zoomNode))").endStatement();
		} else {
			// rebuild the chart
			out.add("build(time || -1)").endStatement();
		}
		out.indentLess().indentLess().add("})").endStatement();

	}

	/**
	 * We will always write the zoom function in, as it can be used even if the user did not request
	 * it to be on available interactively. This way it can be called by API.
	 */
	public void defineChartZoomFunction(ScriptWriter out) {
		out.onNewLine().add("zoom: function(params, time) {").indentMore().indentMore().onNewLine()
				.add("if (params) zoom.on('zoom').call(zoomNode, params, time)").endStatement()
				.add("return d3.zoomTransform(zoomNode)").endStatement();
		out.indentLess().indentLess().onNewLine().add("},");
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
		if (structure.chart.diagram == VisTypes.Diagram.parallel) return true;   // parallel axes use them by default
		if (usesCollapse || usesExpand)
			return true;                            // if we need tree interactivity, need a handler

		for (Param p : structure.vis.tInteraction) {
			String s = p.asString();
			// Only these types create element event handlers
			if (s.equals(Interaction.select.name())
					|| s.equals(call.name())
					|| s.equals(Interaction.collapse.name())) {
				if (targetsElement(p)) return true;
			}
		}
		return false;
	}

	public boolean hasZoomInteractivity() {
		return canZoomX || canZoomY;
	}

	/**
	 * Returns true if we need to prune trees to collapse some nodes
	 *
	 * @return T/F
	 */
	public boolean needsHierarchyPrune() {
		return usesCollapse;
	}

	/**
	 * Returns true if we need to search a tree to find a node to expand out to
	 *
	 * @return T/F
	 */
	public boolean needsHierarchySearch() {
		return usesExpand;
	}

	/**
	 * Adds event handlers for each event, with a list of things to call.
	 *
	 * @param dispatching map of event name to list of commands
	 * @param out
	 */
	private void addDispatchers(Map<String, List<String>> dispatching, ScriptWriter out) {
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

	private String addEvent(LinkedHashMap<String, List<String>> elementEvents, Param p, String functionName) {
		String eventName = p.modifiers().length > 1 ? p.modifiers()[1].toString() : "click";
		addFunctionDefinition(eventName + ".user", functionName + "(d, this, element)", elementEvents);
		if (eventName.equals("mouseover") || eventName.equals("mousemove"))
			addFunctionDefinition("mouseout.user", functionName + "(null, this, element)", elementEvents);
		return eventName;
	}

	private void addFunctionDefinition(String eventName, String definition, Map<String, List<String>> dispatching) {
		List<String> list = dispatching.get(eventName);
		if (list == null) {
			list = new ArrayList<>();
			dispatching.put(eventName, list);
		}
		list.add(definition);
	}

	private String addSelectEvent(LinkedHashMap<String, List<String>> elementEvents, Param p) {
		// We want an event handler on the element -- Also add corresponding mouse out event
		String eventName = p.hasModifiers() ? p.firstModifier().asString() : "click";
		addFunctionDefinition(eventName + ".interact",
				"BrunelD3.select(d, this, element, updateAll)", elementEvents);
		if (eventName.equals("mouseover") || eventName.equals("mousemove"))
			addFunctionDefinition("mouseout.interact",
					"BrunelD3.select(null, this, element, updateAll)", elementEvents);
		return eventName;
	}

	/**
	 * Apply the zoom transform to the scale.
	 * This code is called during the handling of a zoom event (the transform is in the variable 't')
	 *
	 * @param dimension 0 for X, 1 for Y
	 */
	private void applyZoomToScale(int dimension, ScriptWriter out) {
		// Which is the screen dimension for this scale dimension?
		boolean isScreenX = coordinates.isTransposed() ? dimension == 1 : dimension == 0;
		String offset = isScreenX ? "t.x" : "t.y";

		out.add(dimension == 0 ? "scale_x" : "scale_y");

		boolean xCategorical = diagram == null && coordinates.xCategorical;
		boolean yCategorical = diagram == null && coordinates.yCategorical;

		if (dimension == 0 && xCategorical) {
			// We cannot change the domain, so we change the range instead, which we know runs from 0 to the geom extent
			out.add(".range([" + offset + ", " + offset + " + t.k * geom.inner_width])");
		} else if (dimension == 1 && yCategorical) {
			// We cannot change the domain, so we change the range instead, which we know runs from 0 to the geom extent
			out.add(".range([" + offset + ", " + offset + " + t.k * geom.inner_height])");
		} else {
			// D3 allows us to manipulate the domain of the NUMERIC scale using the transform's rescale method
			// We rescale the stored original untransformed scale 'baseScales[dimension]'
			out.add(" =", isScreenX ? "t.rescaleX" : "t.rescaleY");
			out.add("(base_scales[" + dimension + "])");
		}

		out.endStatement();
	}

	private boolean banned(VisElement[] elements, Interaction type) {
		for (VisElement vis : elements) {
			// If the parameter exists, it is only banned if it is specified as "none"
			Param param = getInteractionParam(vis, type);
			if (param != null)
				return param.hasModifiers() && param.firstModifier().asString().equals("none");
		}

		// Otherwise a "none" wins
		for (VisElement vis : elements)
			if (getInteractionParam(vis, Interaction.none) != null) return true;

		// If no information, it is not banned
		return false;
	}

	private boolean[] defaultZooms() {
		// Handle cases when there are diagrams -- the ones that do not fill the space by default are zoomable
		if (diagram == VisTypes.Diagram.network || diagram == VisTypes.Diagram.map
				|| diagram == VisTypes.Diagram.tree) return ZOOM_ALL;

		// Parallel coordinates gets special zooming -- just the "Y"
		if (diagram == VisTypes.Diagram.parallel) return new boolean[]{false, true};

		else if (diagram != null)
			return ZOOM_NONE;

		// we cannot zoom diagrams polar coordinates
		if (coordinates.isPolar()) return ZOOM_NONE;

		// Otherwise allow zoom for non-categorical axes
		return new boolean[]{!coordinates.xCategorical, !coordinates.yCategorical};
	}

	// Return true if we want node expand to fill functionality
	private boolean expandRequested(VisElement[] elements) {
		// Explicit requests in the code are honored. In case of multiple specs, just the first one is used
		for (VisElement vis : elements)
			if (getInteractionParam(vis, Interaction.expand) != null) return true;

		for (VisElement vis : elements)
			if (getInteractionParam(vis, Interaction.none) != null) return false;

		return false;
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
			return new String[]{snapFunction, DEFAULT_SNAP_DISTANCE};   // Default to a quarter the space allowed on screen
		else
			return null;                                    // No snap
	}

	private boolean targetsElement(Param param) {
		if (param == null) return false;                            // No interaction => no targeting
		for (Param p : param.modifiers())
			if (p.asString().startsWith("snap")) return false;      // snap means the handler is attached to background
		return true;                                                // No snap means we do need an element handler
	}

	// Return an array stating whether x and y requested or banned
	// null means no info -- use auto
	private boolean[] zoomRequested(VisElement[] elements) {
		// Explicit requests in the code are honored. In case of multiple specs, just the first one is used
		for (VisElement vis : elements) {
			// "None" means we don't get any interaction
			if (getInteractionParam(vis, Interaction.none) != null) return ZOOM_NONE;

			// Find the panzoom request and use it
			Param param = getInteractionParam(vis, Interaction.panzoom);
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

}
