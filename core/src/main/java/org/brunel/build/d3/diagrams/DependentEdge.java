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
import org.brunel.build.d3.element.EdgeBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;

class DependentEdge extends D3Diagram {

	DependentEdge(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
		super(structure, data, interaction, out);
	}

	public ElementDetails initializeDiagram() {

		if (structure.chart.diagram.isHierarchical)
			return ElementDetails.makeForDiagram(vis, ElementRepresentation.curvedPath, "edge", "graph.links");
		else
			return ElementDetails.makeForDiagram(vis, ElementRepresentation.segment, "edge", "graph.links");
	}

	public void writeDiagramUpdate(ElementDetails details) {
		new EdgeBuilder(out, structure.chart.coordinates.isPolar()).defineLocation();
		addAestheticsAndTooltips(details);
	}


	public String getStyleClasses() {
		return "'diagram hierarchy edge'";
	}

	public String getRowKeyFunction() {
		return  "function(d) { return d.key }";
	}

}
