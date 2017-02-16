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
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Network extends D3Diagram {

	private final ElementStructure nodes;
	private final ElementStructure edges;
	private final String nodeID, fromFieldID, toFieldID;

	public Network(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
		super(structure, data, interaction, out);
		this.nodes = structure;
		this.edges = structure.findDependentEdges();

		if (vis.fKeys.size() > 0) {
			nodeID = vis.fKeys.get(0).asField(nodes.data);
		} else if (vis.fY.size() > 1) {
			nodeID = "#values";
		} else if (vis.positionFields().length > 0) {
			nodeID = vis.positionFields()[0];
		} else {
			throw new IllegalStateException("Networks require nodes to have a key field or position field");
		}

		VisSingle edgesVis = edges.vis;
		if (edgesVis.fKeys.size() > 1) {
			fromFieldID = edgesVis.fKeys.get(0).asField(edges.data);
			toFieldID = edgesVis.fKeys.get(1).asField(edges.data);
		} else if (edgesVis.positionFields().length > 1) {
			fromFieldID = edgesVis.positionFields()[0];
			toFieldID = edgesVis.positionFields()[1];
		} else {
			throw new IllegalStateException("Networks require edges to have two key fields or position fields");
		}
	}

	public void writeBuildCommands() {
		// Determine if we want curved arcs
		String symbol = ModelUtil.getElementSymbol(edges.vis);
		boolean curved = symbol != null && (symbol.contains("arc") || symbol.contains("curve"));

		String density = "";
		if (vis.tDiagramParameters.length > 0) density = ", " + vis.tDiagramParameters[0].asDouble();
		out.onNewLine().add("if (simulation) simulation.stop()").endStatement()
				.add("simulation = BrunelD3.network(graph, elements[" + nodes.index
						+ "], elements[" + edges.index + "], zoomNode, geom, " + curved + density + ")").endStatement();
	}

	public void writePerChartDefinitions() {
		out.add("var graph, simulation;").at(50).comment("Node/edge graph and force simulation");
	}

	public ElementDetails initializeDiagram(String symbol) {
		String edgeDataset = "elements[" + edges.index + "].data()";
		String nodeField = quoted(nodeID), from = quoted(fromFieldID), to = quoted(toFieldID);
		out.add("graph = graph || BrunelData.diagram_Graph.make(processed,", nodeField, ",",
				edgeDataset, ",", from, ",", to, ")").endStatement();

		return ElementDetails.makeForDiagram(vis, ElementRepresentation.largeCircle, "point", "graph.nodes");
	}

	public void writeDiagramEnter(ElementDetails details) {
		out.addChained("attr('r',", details.overallSize, ")");
		D3ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeDiagramUpdate(ElementDetails details) {
		// Handled by the "tick" method of layout
	}

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		D3ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

}
