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
import org.brunel.build.d3.element.D3ElementBuilder;
import org.brunel.build.d3.element.EdgeBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;

class DependentEdge extends D3Diagram {

	private final boolean arrow;    // True if we want arrows

	DependentEdge(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
		super(structure, data, interaction, out);
		String symbol = ModelUtil.getElementSymbol(vis);
		this.arrow = symbol == null || symbol.toLowerCase().contains("arrow");
	}

	public void writePerChartDefinitions() {
		// ensure an arrowhead is defined
		if (arrow)
			out.add("vis.append('svg:defs').selectAll('marker').data(['arrow']).enter()")
					.addChained("append('svg:marker').attr('id', 'arrow')")
					.addChained("attr('viewBox', '0 -6 10 10').attr('orient', 'auto')")
					.addChained("attr('refX', 8).attr('refY', 0)")
					.addChained("attr('markerWidth', 6).attr('markerHeight', 6)")
					.addChained("attr('fill', '#888888')")
					.addChained("append('svg:path').attr('d', 'M0,-4L8,0L0,4')")
					.endStatement();

	}

	public void writeDiagramEnter(ElementDetails details) {
		if (arrow) out.addChained("attr('marker-end', 'url(#arrow)')");
	}

	public ElementDetails initializeDiagram(String symbol) {
		return ElementDetails.makeForDiagram(vis, ElementRepresentation.curvedPath, "edge", "graph.links");
	}

	public void writeDiagramUpdate(ElementDetails details) {
		new EdgeBuilder(out, structure.chart.coordinates.isPolar()).defineLocation();
		D3ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		D3ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public String getStyleClasses() {
		return "'diagram hierarchy edge'";
	}

	public String getRowKeyFunction() {
		return "function(d) { return d.key }";
	}

}
