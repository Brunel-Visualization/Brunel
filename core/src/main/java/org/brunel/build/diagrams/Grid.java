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
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

import static org.brunel.build.element.ElementRepresentation.spaceFillingCircle;

class Grid extends Bubble {

	private int rows = 0, columns = 0;                  // size of the grid (0 -> "choose for me")
	private double aspect = 1;                          // desired aspect ratio of the grid cells

	Grid(ElementStructure structure) {
		super(structure);

		for (Param p : vis.tDiagramParameters) {
			String s = p.asString();
			if (s.equals("aspect") && p.hasModifiers())
				aspect = p.firstModifier().asDouble();
			else if (s.equals("rows") && p.hasModifiers())
				rows = (int) p.firstModifier().asDouble();
			if (s.equals("columns") && p.hasModifiers())
				columns = (int) p.firstModifier().asDouble();
		}
	}

	public void defineCoordinateFunctions(ElementDetails details, ScriptWriter out) {
		// The default symbol size is 301% -- but this means it overflows the grid.
		// So (and this is a hack) we search for that exact default (301%) and remove it
		GeomAttribute size = details.overallSize;
		String replace = size.definition().replace("3.01", "1");
		details.overallSize = size.isFunc() ? GeomAttribute.makeFunction(replace)
				: GeomAttribute.makeConstant(replace);

		defineXYR("scale_x(d.x)", "scale_y(d.y)", "scale_x(d.r) - scale_x(0)", details, out);
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Define hierarchy and grid data structures");

		makeHierarchicalTree(false, out);

		out.add("var gridLabels = BrunelD3.gridLayout(tree, [geom.inner_width, geom.inner_height], "
				+ rows + ", " + columns + ", " + aspect + ")").endStatement();
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, spaceFillingCircle, "point", "tree.leaves()");
	}

	public void writeDiagramEnter(ElementDetails details, LabelBuilder labelBuilder, ScriptWriter out) {
		// Nothing
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		// Classes defined for CSS
		out.addChained("attr('class', function(d) { return (d.children ? 'element L' + d.depth : 'leaf element " + element.name() + "') })");

		ElementBuilder.definePointLikeMark(details, structure, out, false);
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeAdditionalUpdateStatements(ElementDetails details, ScriptWriter out) {
		LabelBuilder labelBuilder = new LabelBuilder(structure, out);
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public boolean needsDiagramExtras() {
		return true;
	}
}
