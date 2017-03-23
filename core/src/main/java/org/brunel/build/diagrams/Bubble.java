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
import org.brunel.build.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

class Bubble extends D3Diagram {

	public Bubble(ElementStructure structure) {
		super(structure);
	}

	public void defineCoordinateFunctions(ElementDetails details, ScriptWriter out) {
		details.overallSize = GeomAttribute.makeConstant("");                                // ensure it gets replaced
		defineXYR("scale_x(d.x)", "scale_y(d.y)", "scale_x(d.r) - scale_x(0)", details, out);
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Define bubble (hierarchy) data structures");
		makeHierarchicalTree(false, out);
		out.add("var pack = d3.pack().size([geom.inner_width, geom.inner_height])").endStatement();
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, ElementRepresentation.spaceFillingCircle, "point", "pack(tree).descendants()");
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		writeHierarchicalClass(out);
		out.addChained("filter(function(d) { return d.depth })");

		// Classes defined for CSS
		out.addChained("attr('class', function(d) { return (d.children ? 'element L' + d.depth : 'leaf element " + element.name() + "') })");

		ElementBuilder.definePointLikeMark(details, structure, out);
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeDiagramEnter(ElementDetails details, LabelBuilder labelBuilder, ScriptWriter out) {
		// We place everything at its parent when it enters the system
		out.addChained("filter(function(d) { return d.parent })")
				.addChained("attr('x', function(d) { return scale_x(d.parent.x) })")
				.addChained("attr('y', function(d) { return scale_y(d.parent.y) })");

		if (details.representation.isBoxlike())
			out.addChained("attr('width', 0).attr('height', 0)");
		else
			out.addChained("attr('r', 0)");
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

}
