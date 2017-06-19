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

package org.brunel.build.diagrams;

import org.brunel.action.Param;
import org.brunel.build.LabelBuilder;
import org.brunel.build.ScaleBuilder;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.style.StyleTarget;

import java.util.List;

class Tree extends D3Diagram {

	private final Method method;                                    // How to draw it
	private final int labelSize;                                    // Size to leave for labels
	private final double sizeFactor;                                // Scaling for node sizes
	private int pad;                                            	// Pad size
	private final boolean usesSize;                                 // True is size is used

	public Tree(ElementStructure structure) {
		super(structure);
		if (vis.coords == Coordinates.polar) method = Method.polar;
		else method = Method.leftRight;

		usesSize = !vis.fSize.isEmpty();
		labelSize = LabelBuilder.estimateLabelLength(structure.vis.itemsLabel, structure.data) * 6;
		sizeFactor = getSize(vis.fSize);

		StyleTarget target = StyleTarget.makeElementTarget("point", "element");
		ModelUtil.Size size = ModelUtil.getSize(vis, target, "size");
		pad = (int) Math.ceil(size == null ? structure.chart.defaultPointSize() : size.value(10) / 2);
		if (usesSize) pad = (int) Math.ceil(pad * sizeFactor);
		pad += 3;			// A little extra for borders etc.
	}

	private double getSize(List<Param> fSize) {
		// Guesses the maximum size a node might be for the size aesthetic
		if (fSize.isEmpty()) return 1;							// No scaling
		if (!fSize.get(0).hasModifiers()) return 1;				// range is [0,1] so max is one

		// we have a defined maximum size, so return it
		Double[] sizes = ScaleBuilder.getSizes(fSize.get(0).modifiers()[0].asList());
		return sizes[sizes.length-1];
	}

	public void defineCoordinateFunctions(ElementDetails details, ScriptWriter out) {
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

		defineXYR(cx, cy, "d.data.radius = " + rr.definition(), details, out);
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Define tree (hierarchy) data structures");
		makeHierarchicalTree(true, out);
		out.add("var treeLayout = d3.tree()");

		if (method == Method.polar) {
			// Pad all around for labels
			out.addChained("size([2*Math.PI, geom.inner_radius-" + (pad + labelSize) + "])")
					.addChained("separation(function(a,b) { return (a.parent == b.parent ? 1 : 2) / a.depth })");
		} else {

			// Horizontal trees need extra space for labels and potentially a large root node
			int adjustedWidth = 2 * pad + labelSize;


			// Trees default to top-bottom, hence the reversal of coordinates
			out.addChained("size([geom.inner_height-" + 2 * pad + ", geom.inner_width-" + adjustedWidth + "])");
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

	public boolean needsDiagramExtras() {
		return true;
	}

	public boolean needsDiagramLabels() {
		return true;
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		writeHierarchicalClass(out);
		ElementBuilder.definePointLikeMark(details, structure, out);
		ElementBuilder.writeElementAesthetics(details, true, vis, out);

		// If we have edges defined as an element, we use those, otherwise add the following
		if (getDependentEdges() == null) {
			out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
			out.add("diagramExtras.attr('class', 'diagram tree edge')").endStatement();
			out.add("function edgeKey(d) { return nodeKey(d.source) + '%' + nodeKey(d.target) }").endStatement();
			out.add("var edgeGroup = diagramExtras.selectAll('path').data(tree.links(), edgeKey)")
					.endStatement();

			DependentEdge.write(true, structure.chart, out, "edgeGroup");
			ElementBuilder.writeRemovalOnExit(out, "edgeGroup");

			LabelBuilder labelBuilder = new LabelBuilder(structure, out);
			labelBuilder.addTreeInternalLabelsOutsideNode(
					method == Method.leftRight || !usesSize ? "bottom" : "center"
			);
		}

	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public void writePerChartDefinitions(ScriptWriter out) {
		super.writePerChartDefinitions(out);
		out.add("var graph;").comment("The tree with links");
	}

	private enum Method {leftRight, topBottom, polar}

}
