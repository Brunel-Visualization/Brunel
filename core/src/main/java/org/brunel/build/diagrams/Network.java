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
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisSingle;

class Network extends D3Diagram {

	private final ElementStructure nodes;
	private final String nodeID;

	public Network(ElementStructure structure) {
		super(structure);
		this.nodes = structure;

		if (vis.fKeys.size() > 0) {
			nodeID = vis.fKeys.get(0).asField(nodes.data);
		} else if (vis.fY.size() > 1) {
			nodeID = "#values";
		} else if (vis.positionFields().length > 0) {
			nodeID = vis.positionFields()[0];
		} else {
			throw new IllegalStateException("Networks require nodes to have a key field or position field");
		}
	}

	public void writeBuildCommands(ScriptWriter out) {
		DependentEdge dependentEdge = (DependentEdge) getDependentEdges().diagram;

		// Determine if we want curved arcs and add a density parameter if requested
		boolean curved = dependentEdge.curved;
		String density = vis.tDiagramParameters.length == 0 ? "" : ", " + vis.tDiagramParameters[0].asDouble();

		out.onNewLine().add("if (simulation) simulation.stop()").endStatement()
				.add("simulation = BrunelD3.network(graph, elements[" + nodes.index
						+ "], elements[" + getDependentEdges().index + "], zoomNode, geom, " + curved + density + ")").endStatement();
	}

	public void writePerChartDefinitions(ScriptWriter out) {
		out.add("var graph, simulation;").comment("Node/edge graph and force simulation");
	}

	public void writeDataStructures(ScriptWriter out) {
		ElementStructure edges = getDependentEdges();

		VisSingle edgesVis = edges.vis;

		String edgeDataset = "elements[" + edges.index + "].data()";

		String fromFieldID, toFieldID;
		if (edgesVis.fKeys.size() > 1) {
			fromFieldID = edgesVis.fKeys.get(0).asField(edges.data);
			toFieldID = edgesVis.fKeys.get(1).asField(edges.data);
		} else if (edgesVis.positionFields().length > 1) {
			fromFieldID = edgesVis.positionFields()[0];
			toFieldID = edgesVis.positionFields()[1];
		} else {
			throw new IllegalStateException("Networks require edges to have two key fields or position fields");
		}

		String nodeField = quoted(nodeID), from = quoted(fromFieldID), to = quoted(toFieldID);
		out.add("graph = graph || BrunelData.diagram_Graph.make(processed,", nodeField, ",",
				edgeDataset, ",", from, ",", to, ")").endStatement();
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, ElementRepresentation.largeCircle, "point", "graph.nodes");
	}

	public void writeDiagramEnter(ElementDetails details, ScriptWriter out) {
		out.addChained("attr('r',", details.overallSize, ")");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		// Handled by the "tick" method of layout
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

}
