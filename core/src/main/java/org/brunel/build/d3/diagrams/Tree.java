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

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.element.D3ElementBuilder;
import org.brunel.build.d3.element.EdgeBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.d3.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.style.StyleTarget;

class Tree extends D3Diagram {

	private enum Method {leftRight, topBottom, polar}

	private final Method method;                                    // How to draw it
	private final int labelSize;                                    // Size to leave for labels
	private final int pad;                                            // Pad size
	private final boolean usesSize;                                 // True is size is used

	public Tree(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
		super(structure, data, interaction, out);
		if (vis.coords == Coordinates.polar) method = Method.polar;
		else method = Method.leftRight;
		labelSize = labelBuilder.estimateLabelLength() * 6;
		usesSize = !vis.fSize.isEmpty();

		StyleTarget target = StyleTarget.makeElementTarget("point", "element");
		ModelUtil.Size size = ModelUtil.getSize(vis, target, "size");
		pad = size == null ? 10 : (int) size.value(10) / 2 + 3;
	}

	public void writePerChartDefinitions() {
		super.writePerChartDefinitions();
		out.add("var graph;").at(50).comment("The tree with links");
	}

	public ElementDetails initializeDiagram() {
		out.comment("Define tree (hierarchy) data structures");
		makeHierarchicalTree(true);
		out.add("var treeLayout = d3.tree()");

		if (method == Method.polar) {
			// Pad all around for labels
			out.addChained("size([2*Math.PI, geom.inner_radius-" + (pad + labelSize) + "])")
					.addChained("separation(function(a,b) { return (a.parent == b.parent ? 1 : 2) / a.depth })");
		} else {
			// Trees default to top-bottom, hence the reversal of coordinates
			out.addChained("size([geom.inner_height-" + 2 * pad + ", geom.inner_width-" + (2 * pad + labelSize) + "])");
		}

		out.endStatement();

		out.add("var treeNodes = treeLayout(tree).descendants()").endStatement();

		// Run through the tree nodes and copy the locations to the underlying data structure.
		// When not polar, also pad the items

		out.add("BrunelD3.copyTreeLayoutInfo(treeNodes, graph, " + (method == Method.polar ? "0" : pad) + ")")
				.endStatement();

		if (usesSize && vis.positionFields().length != 0) {
			// Redefine size to use the node value for the "fields" tree case
			out.add("size = function(d) { return scale_size(d.value) }").endStatement();
		}

		ElementRepresentation rep;
		if (ModelUtil.getElementSymbol(vis) != null)
			rep = ElementRepresentation.symbol;
		else if (method == Method.leftRight)
			rep = ElementRepresentation.pointLikeCircle;
		else
			rep = ElementRepresentation.largeCircle;

		return ElementDetails.makeForDiagram(vis, rep, "point", "treeNodes");
	}

	public void writeDiagramEnter(ElementDetails details) {
		out.addChained("filter(function(d) { return d.parent })");       // Only if it has a parent
		writeNodePlacement(structure.details, "d.parent");              // place it at parent position
	}

	public void writeDiagramUpdate(ElementDetails details) {
		writeHierarchicalClass();

		writeNodePlacement(details, "d");

		out.addChained("attr('r', " + details.overallSize.halved() + ")");
		addAestheticsAndTooltips(details);

		// If we have edges defined as an element, we use those, otherwise add the following
		if (structure.findDependentEdges() == null) {
			out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
			out.add("diagramExtras.attr('class', 'diagram tree edge')").endStatement();
			out.add("function edgeKey(d) { return nodeKey(d.source) + '%' + nodeKey(d.target) }").endStatement();
			out.add("var edgeGroup = diagramExtras.selectAll('path').data(tree.links(), edgeKey)")
					.endStatement();

			new EdgeBuilder(out, method == Tree.Method.polar).write("edgeGroup");
			D3ElementBuilder.writeRemovalOnExit(out, "edgeGroup");

			labelBuilder.addTreeInternalLabelsOutsideNode(
					method == Method.leftRight || !usesSize ? "bottom" : "center"
			);
		}

	}

	private void writeNodePlacement(ElementDetails details, String d) {

		String cx, cy;            // Functions defining the locations of node centers
		if (method == Method.leftRight) {
			cx = "scale_x(" + d + ".y)";
			cy = "scale_y(" + d + ".x)";
		} else if (method == Method.topBottom) {
			cx = "scale_x(" + d + ".x)";
			cy = "scale_y(" + d + ".y)";
		} else {
			cx = "scale_x(" + d + ".y * Math.cos(" + d + ".x))";
			cy = "scale_y(" + d + ".y * Math.sin(" + d + ".x))";
		}

		String symbolName = ModelUtil.getElementSymbol(vis);
		if (symbolName != null) {
			// Add a translate to place it in the right location
			out.addChained("attr('transform', function(d) { return 'translate(' + "
					+ cx + " + ', ' + " + cy + " + ')' })");

			symbolName = Data.quote(symbolName);

			if (details == null) {
				out.addChained("attr('d', BrunelD3.symbol(" + symbolName + ", 10))");
			} else {
				GeomAttribute size = details.overallSize.halved();
				if (size.isFunc()) {
					// The size changes, so we must call the function
					out.addChained("attr('d', function(d) { return BrunelD3.symbol(" + symbolName + ", " +
							size.definition() + ") })");
				} else {
					// Fixed symbol -- no function needed
					out.addChained("attr('d', BrunelD3.symbol(" + symbolName + ", " + size + "))");
				}
			}
		} else {
			out.addChained("attr('cx', function(d) { return " + cx + " })")
					.addChained("attr('cy', function(d) { return " + cy + " })");
		}
	}

	public boolean needsDiagramExtras() {
		return true;
	}

	public boolean needsDiagramLabels() {
		return true;
	}

}
