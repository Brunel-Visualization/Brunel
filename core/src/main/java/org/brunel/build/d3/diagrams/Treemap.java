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

import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.element.ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;

class Treemap extends D3Diagram {

	public Treemap(ElementStructure structure, Dataset data, ScriptWriter out) {
		super(structure, data);
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Define treemap (hierarchy) data structures");
		makeHierarchicalTree(false, out);

		// Create the d3 layout
		out.add("var treemap = d3.treemap().tile(d3.treemapResquarify)")
				.addChained("size([geom.inner_width, geom.inner_height])")
				.addChained("padding(function(d) { return d.depth < 3 ? 2*d.depth : 0} )")
				.addChained("paddingTop(function(d) { return d.depth ==1 ? 15 : (d.depth == 2) ? 12 : 0})")
				.endStatement();
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, ElementRepresentation.rect, "polygon", "treemap(tree).descendants()");
	}

	public void writeDiagramEnter(ElementDetails details, ScriptWriter out) {
		out.addChained("filter(function(d) { return d.parent })")       // Only if it has a parent
				.addChained("attr('x', function(d) { return scale_x((d.parent.x0+d.parent.x1)/2) })")
				.addChained("attr('y', function(d) { return scale_y((d.parent.y0+d.parent.y1)/2) })")
				.addChained("attr('width', 0).attr('height', 0)");
	}

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
		labelBuilder.addTreeInternalLabelsInsideNode();
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		writeHierarchicalClass(out);
		out.addChained("attr('x', function(d) { return scale_x(d.x0) })")
				.addChained("attr('y', function(d) { return scale_y(d.y0) })")
				.addChained("attr('width', function(d) { return scale_x(d.x1) - scale_x(d.x0) })")
				.addChained("attr('height', function(d) { return scale_y(d.y1) - scale_y(d.y0) })");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public boolean needsDiagramLabels() {
		return true;
	}

}
