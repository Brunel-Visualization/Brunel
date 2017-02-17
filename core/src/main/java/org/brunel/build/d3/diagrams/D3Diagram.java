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

package org.brunel.build.d3.diagrams;

import org.brunel.action.Param;
import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

import java.util.ArrayList;
import java.util.List;

public abstract class D3Diagram {
	public static D3Diagram make(ElementStructure structure, D3Interaction interaction, ScriptWriter out) {
		VisSingle vis = structure.vis;
		Dataset data = structure.data;

		if (vis.tDiagram == null) {
			// The edges defined for a graph or network are treated as an implicit diagram
			if (structure.chart.diagram != null && structure.isDependentEdge())
				return new DependentEdge(structure, data, interaction, out);
			else return null;

		}
		if (vis.tDiagram == Diagram.bubble) return new Bubble(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.chord) return new Chord(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.cloud) return new Cloud(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.tree) return new Tree(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.parallel) return new ParallelCoordinates(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.gridded) return new Grid(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.table) return new Table(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.treemap) return new Treemap(structure, data, interaction, out);
		if (vis.tDiagram == Diagram.network)
			return new Network(structure, data, interaction, out);
		if (isMapLabels(vis))
			return new GeoMapLabels(structure, data, interaction, out);
		else if (vis.tDiagram == Diagram.map) {

			return new GeoMap(structure, data, structure.geo, interaction, out);
		}
		throw new IllegalStateException("Unknown diagram: " + vis.tDiagram);
	}

	// Identify a diagram that is for map labels
	public static boolean isMapLabels(VisSingle vis) {
		return vis.tDiagram == Diagram.map && vis.tDiagramParameters.length == 1 && vis.tDiagramParameters[0].asString().equals("labels");
	}

	final ScriptWriter out;
	final Param size;
	final Element element;
	final VisSingle vis;
	final D3LabelBuilder labelBuilder;
	final D3Interaction interaction;
	final String[] position;
	final ElementStructure structure;
	private boolean isHierarchy;

	D3Diagram(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
		this.structure = structure;
		this.vis = structure.vis;
		this.out = out;
		this.size = vis.fSize.isEmpty() ? null : vis.fSize.get(0);
		this.position = vis.positionFields();
		this.element = vis.tElement;
		this.interaction = interaction;
		this.labelBuilder = new D3LabelBuilder(vis, out, data);

	}

	public void defineCoordinateFunctions(ElementDetails details) {
		// By default, do nothing
	}

	public String getRowKeyFunction() {
		return vis.tDiagram.isHierarchical ? "nodeKey" : "function(d) { return d.key }";
	}

	public String getStyleClasses() {
		// Define the classes for this group; note that we flag hierarchies specially also
		String classes = "diagram " + vis.tDiagram.name();
		return "'" + (isHierarchy ? classes + " hierarchy" : classes) + "'";
	}

	public boolean needsDiagramExtras() {
		// By default, no extras needed
		return false;
	}

	public boolean needsDiagramLabels() {
		// By default, no labels needed
		return false;
	}

	public void preBuildDefinitions() {
		// By default, do nothing
	}

	public void writeBuildCommands() {
		// By default, do nothing
	}

	public abstract ElementDetails initializeDiagram(String symbol);

	public abstract void writeDiagramUpdate(ElementDetails details);

	/**
	 * This is called when
	 *
	 * @param details
	 */
	public void writeDiagramEnter(ElementDetails details) {
		// By default, nothing is needed
	}

	public abstract void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder);

	public void writePerChartDefinitions() {
		if (vis.tDiagram != null && vis.tDiagram.isHierarchical) {
			out.add("var tree, expandState = [], collapseState = {};").at(50).comment("collapse state maps node IDs to true/false");
		}
	}

	void makeHierarchicalTree(boolean definedHierarchy) {
		Integer prune = findPruneParameter(vis.tDiagramParameters);
		String pruneValue;
		if (prune == null)
			pruneValue = null;
		else if (prune < 1) {
			if (vis.coords == VisTypes.Coordinates.polar)
				pruneValue = "geom.inner_width * geom.inner_height / 1000";
			else if (vis.coords == VisTypes.Coordinates.transposed)
				pruneValue = "geom.inner_width / 10";
			else
				pruneValue = "geom.inner_height / 10";
		} else {
			pruneValue = prune.toString();
		}

		String[] positionFields = vis.positionFields();
		String sizeParam = size == null ? null : Data.quote(size.asField());

		if (positionFields.length != 0 || vis.fKeys.isEmpty()) {
			// Positions have been defined using fields, so we create a hierarchy by treating them as categories
			// and nesting categories of one field within the field at the next level up.
			String fieldsList = positionFields.length == 0 ? "" : ", " + quoted(positionFields);
			defineOrDeclareHierarchy(definedHierarchy);
			out.add("graph = BrunelData.diagram_Hierarchical.makeByNestingFields(processed, "
					+ sizeParam + fieldsList + ")")
					.endStatement();
		} else {
			// We have no positions, so we must find a dependent element with keys that define edges building the hierarchy
			ElementStructure edges = structure.findDependentEdges();

			if (vis.fKeys.isEmpty()) throw new IllegalStateException("Tree needs keys for ids");
			if (edges == null)
				throw new IllegalStateException("Tree needs edges defined by another element if no positions defined");

			String nodeIDField = Data.quote(vis.fKeys.get(0).asField());
			String edge1Field = Data.quote(edges.vis.fKeys.get(0).asField());
			String edge2Field = Data.quote(edges.vis.fKeys.get(1).asField());

			defineOrDeclareHierarchy(definedHierarchy);
			out.add("graph = BrunelData.diagram_Hierarchical.makeByEdges(processed, "
					+ nodeIDField + ", " + sizeParam + ", elements[" + edges.index + "].data(), " +
					edge1Field + ", " + edge2Field + ")")
					.endStatement();

		}

		if (interaction.needsHierarchySearch())
			out.add("var targetNode = expandState.length ? graph.find(expandState[expandState.length-1]) : graph.root")
					.endStatement();
		else
			out.add("var targetNode = graph.root").endStatement();

		out.add("tree = d3.hierarchy(targetNode).sum(function(d) { return d.value })")
				.endStatement();

		boolean reduceSizes = vis.tDiagram == Diagram.bubble || vis.tDiagram == Diagram.treemap;

		// The collapseState map contains a map of keys to true / false for user-specified collapsing

		if (interaction.needsHierarchyPrune()) {
			if (pruneValue == null)
				out.add("BrunelD3.prune(tree, collapseState, " + reduceSizes + ")").endStatement();
			else
				out.add("BrunelD3.prune(tree, collapseState, " + reduceSizes + ", first ? " + pruneValue + ": null)").endStatement();
		}
		out.add("function nodeKey(d) { return d.data.key == null ? data._key(d.data.row) : d.data.key }").endStatement();
		isHierarchy = true;
	}

	private void defineOrDeclareHierarchy(boolean definedHierarchy) {
		out.add("var first = (!tree)");
		if (definedHierarchy) {
			// Already defined so set the value on a new line
			out.endStatement();
		} else {
			// not defined, so add to definition list
			out.add(", ");
		}
	}

	private Integer findPruneParameter(Param[] params) {
		for (Param p : params)
			if (p.asString().equals("prune")) {
				if (p.hasModifiers()) return p.firstModifier().asInteger();
				else return -1;
			}
		return null;
	}

	protected String quoted(String... items) {
		List<String> p = new ArrayList<>();
		for (String s : items) p.add(Data.quote(s));
		return Data.join(p);
	}

	// Define the class based on hierarchy
	protected void writeHierarchicalClass() {
		out.addChained("attr('class', function(d) { return (d.collapsed ? 'collapsed ' : '') "
				+ "+ (d.data.children ? 'element L' + d.depth : 'leaf element " + element.name() + "') })");
	}

	/**
	 * Define coordinate functions to be used in the diagram
	 *
	 * @param x       the x coordinate
	 * @param y       the x coordinate
	 * @param r       the radius
	 * @param details override details in here
	 */
	protected void defineXYR(String x, String y, String r, ElementDetails details) {
		out.onNewLine().comment("Define Coordinate functions");

		// We will substitute in our radius for the default. This is a hack depending on the name of the geom attribute
		// function(d) { return size(d) * 3.0 * geom.default_point_size}

		String def = details.overallSize.definition();
		String newDef = def.replace("geom.default_point_size", "(" + r + ")");
		if (def.equals(newDef)) {
			// Replacement failed -- just use the new def
			newDef = r;
		}

		out.add("function r(d) { return " + newDef + " }").endStatement();

		if (details.representation.isBoxlike()) {
			// For symbols and rectangles, define the lower and upper extents for each dimension
			out.add("function x1(d) { return " + x + " - r(d) }").endStatement()
					.add("function x2(d) { return " + x + " + r(d) }").endStatement()
					.add("function y1(d) { return " + y + " - r(d) }").endStatement()
					.add("function y2(d) { return " + y + " + r(d) }").endStatement();

			// Known extent locations to use
			details.x.left = GeomAttribute.makeFunction("x1(d)");
			details.x.right = GeomAttribute.makeFunction("x2(d)");
			details.y.left = GeomAttribute.makeFunction("y1(d)");
			details.y.right = GeomAttribute.makeFunction("y2(d)");
		} else {
			// For everything else (circle-like) just the center and radius is needed
			out.add("function x(d) { return " + x + " }").endStatement()
					.add("function y(d) { return " + y + " }").endStatement();

			// Known center locations to use
			details.x.center = GeomAttribute.makeFunction("x(d)");
			details.y.center = GeomAttribute.makeFunction("y(d)");
			details.overallSize = GeomAttribute.makeFunction("r(d) * 2");
		}

		details.x.sizeFunction = null;
	}

}

