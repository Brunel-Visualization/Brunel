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
import org.brunel.build.d3.element.D3ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;

import static org.brunel.build.d3.element.ElementRepresentation.spaceFillingCircle;

class Grid extends Bubble {

	private int rows = 0, columns = 0;                  // size of the grid (0 -> "choose for me")
	private double aspect = 1;                          // desired aspect ratio of the grid cells

	Grid(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
		super(structure, data, interaction, out);

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

	public void writeDiagramEnter(ElementDetails details) {
		// Nothing
	}

	public ElementDetails initializeDiagram(String symbol) {
		out.comment("Define hierarchy and grid data structures");

		makeHierarchicalTree(false);

		out.add("var gridLabels = BrunelD3.gridLayout(tree, [geom.inner_width, geom.inner_height], "
				+ rows + ", " + columns + ", " + aspect + ")").endStatement();

		return ElementDetails.makeForDiagram(vis, spaceFillingCircle, "point", "tree.leaves()");
	}

	public void defineCoordinateFunctions(ElementDetails details) {
		// The default symbol size is 301% -- but this means it overflows the grid.
		// So (and this is a hack) we search for that exact default (301%) and remove it
		GeomAttribute size = details.overallSize;
		String replace = size.definition().replace("3.01", "1");
		details.overallSize = size.isFunc() ? GeomAttribute.makeFunction(replace)
				: GeomAttribute.makeConstant(replace);

		defineXYR("scale_x(d.x)", "scale_y(d.y)", "scale_x(d.r) - scale_x(0)", details);
	}

	public void writeDiagramUpdate(ElementDetails details) {
		// Classes defined for CSS
		out.addChained("attr('class', function(d) { return (d.children ? 'element L' + d.depth : 'leaf element " + element.name() + "') })");

		D3ElementBuilder.definePointLikeMark(details, structure, out);
		D3ElementBuilder.writeElementAesthetics(details, true, vis, out);
		D3ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
		labelBuilder.addGridLabels();
	}

	public boolean needsDiagramExtras() {
		return true;
	}
}
