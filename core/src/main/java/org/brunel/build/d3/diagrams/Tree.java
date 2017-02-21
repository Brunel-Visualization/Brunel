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
import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.element.ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.d3.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
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


	public void writeDataStructures() {
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
	}

	public ElementDetails makeDetails() {
		ElementRepresentation rep;
		if (method == Method.leftRight)
			rep = ElementRepresentation.pointLikeCircle;
		else
			rep = ElementRepresentation.largeCircle;

		return ElementDetails.makeForDiagram(structure, rep, "point", "treeNodes");
	}

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public void defineCoordinateFunctions(ElementDetails details) {
		String cx, cy;            // Functions defining the locations of node centers
		if (method == Method.leftRight) {
			cx = "scale_x(d.y)";
			cy = "scale_y(d.x)";
		} else if (method == Method.topBottom) {
			cx = "scale_x(d.x)";
			cy = "scale_y(d.y)";
		} else {
			cx = "scale_x(d.y * Math.cos(d.x))";
			cy = "scale_y(d.y * Math.sin(d.x))";
		}

		GeomAttribute rr = details.overallSize.halved();

		defineXYR(cx, cy, "d.data.radius = " + rr.definition(), details);
	}

	public void writeDiagramUpdate(ElementDetails details) {
		writeHierarchicalClass();
		ElementBuilder.definePointLikeMark(details, structure, out);
		ElementBuilder.writeElementAesthetics(details, true, vis, out);


		// If we have edges defined as an element, we use those, otherwise add the following
		if (structure.findDependentEdges() == null) {
			out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
			out.add("diagramExtras.attr('class', 'diagram tree edge')").endStatement();
			out.add("function edgeKey(d) { return nodeKey(d.source) + '%' + nodeKey(d.target) }").endStatement();
			out.add("var edgeGroup = diagramExtras.selectAll('path').data(tree.links(), edgeKey)")
					.endStatement();

			DependentEdge.write(true, structure.chart.coordinates.isPolar(), out, "edgeGroup");
			ElementBuilder.writeRemovalOnExit(out, "edgeGroup");

			labelBuilder.addTreeInternalLabelsOutsideNode(
					method == Method.leftRight || !usesSize ? "bottom" : "center"
			);
		}

	}

	public boolean needsDiagramExtras() {
		return true;
	}

	public boolean needsDiagramLabels() {
		return true;
	}

}
