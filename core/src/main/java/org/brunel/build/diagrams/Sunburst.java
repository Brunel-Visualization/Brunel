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

import org.brunel.build.LabelBuilder;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.style.StyleTarget;

class Sunburst extends D3Diagram {

	private final Padding padding;

	public Sunburst(ElementStructure structure) {
		super(structure);
		padding = ModelUtil.getPadding(vis, StyleTarget.makeElementTarget(null, "element"), 0);
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Define sunburst (hierarchy) data structures");
		makeHierarchicalTree(true, out);

		// Create the d3 layout
		out.add("d3.partition()")
				.addChained("size([2*Math.PI,geom.inner_radius])")
				.add("(tree)")
				.endStatement();
		out.add("var maxDepth = 0; tree.descendants().forEach(function(i) {maxDepth = Math.max(maxDepth, i.depth)})")
				.endStatement();
		out.add("function lens(x) { return Math.sin( Math.PI / 2 * (x-0.5) / (maxDepth-0.5), 0.25) }")
				.comment("inner circles are bigger");
	}

	public void writePerChartDefinitions(ScriptWriter out) {
		super.writePerChartDefinitions(out);
		out.add("var graph;").comment("The tree with links");
	}

	public ElementDetails makeDetails() {
		return new ElementDetails(structure.vis, ElementRepresentation.generalPath, "wedge", "tree.descendants()", true);
	}

	public void defineCoordinateFunctions(ElementDetails details, ScriptWriter out) {
		// Define the arcs used for the wedge

		// Padding strings
		String padA = padding.top == 0 ? "" : (padding.top < 0 ? "" + padding.top : "+" + padding.top);
		String padB = padding.bottom == 0 ? "" : (padding.bottom < 0 ? "+" + (-padding.bottom) : "-" + padding.top);

		out.add("var path = d3.arc()")
				.addChained("startAngle(function(d) { return scale_x(d.x0); })")
				.addChained("endAngle(function(d) { return scale_x(d.x1); })")
				.addChained("innerRadius(function(d) { return geom.inner_radius * scale_y(lens(d.depth))" + padA + "; })")
				.addChained("outerRadius(function(d) { return geom.inner_radius * scale_y(lens(d.depth+1))" + padB + "; })")
				.endStatement();
	}

	public boolean needsDiagramLabels() {
		return true;
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		writeHierarchicalClass(out);
		out.addChained("attr('d', path)");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

}
