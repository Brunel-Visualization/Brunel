/*
 * Copyright (c) 2016 IBM Corporation and others.
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

package org.brunel.build.d3.element;

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.d3.diagrams.GeoMap;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisTypes;

import static org.brunel.model.VisTypes.Diagram.map;

public class DiagramElementBuilder extends ElementBuilder {

	private final D3Diagram diagram;

	public DiagramElementBuilder(ElementStructure structure, ScriptWriter out, D3ScaleBuilder scales, D3Interaction interaction, D3Diagram diagram) {
		super(structure, interaction, scales, out);
		this.diagram = diagram;
	}

	public ElementDetails makeDetails() {
		return diagram.makeDetails(getCommonSymbol());
	}

	public boolean needsDiagramExtras() {
		return diagram.needsDiagramExtras();
	}

	public boolean needsDiagramLabels() {
		return diagram.needsDiagramLabels();
	}

	public void preBuildDefinitions() {
		diagram.preBuildDefinitions();
	}

	public void writeBuildCommands() {
		diagram.writeBuildCommands();
	}

	public void writeDiagramDataStructures() {
		diagram.writeDataStructures();
	}

	public void writePerChartDefinitions() {
		diagram.writePerChartDefinitions();
	}

	protected void defineLabeling(ElementDetails details) {
		out.onNewLine().ln().comment("Define labeling for the selection")
				.onNewLine().add("function label(selection, transitionMillis) {")
				.indentMore().onNewLine();
			diagram.writeLabelsAndTooltips(details, labelBuilder);
		out.indentLess().onNewLine().add("}").ln();
	}

	/* The key function ensure we have object constancy when animating */
	protected String getKeyFunction() {
		return diagram.getRowKeyFunction();
	}


	protected void defineAllElementFeatures(ElementDetails details) {
		if (vis.tElement == VisTypes.Element.point && vis.tDiagram == map) {
			// Points on maps do need the coordinate functions
			writeCoordinateFunctions(details);
		} else {
			// Set the diagram group class for CSS
			out.add("main.attr('class',", diagram.getStyleClasses(), ")").endStatement();
			diagram.defineCoordinateFunctions(details);
		}
	}

	protected void writeDiagramEntry(ElementDetails details) {
			diagram.writeDiagramEnter(details);
	}

	protected void defineUpdateState(ElementDetails details) {
		// Define the update to the merged data
		out.onNewLine().ln().comment("Define selection update operations on merged data")
				.onNewLine().add("function updateState(selection) {").indentMore()
				.onNewLine().add("selection").onNewLine();
		if (diagram instanceof GeoMap) {
			writeCoordinateDefinition(details);
			writeElementAesthetics(details, true, vis, out);
		}
		diagram.writeDiagramUpdate(details);
		out.endStatement();
		out.indentLess().onNewLine().add("}").ln();
	}
}
