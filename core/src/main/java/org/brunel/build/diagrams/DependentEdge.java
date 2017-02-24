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
import org.brunel.model.VisTypes;

class DependentEdge extends D3Diagram {

	public static void write(boolean curved, boolean polar, ScriptWriter out, String groupName) {
		// Create paths for the added items, and grow the from the source
		out.add("var added = " + "edgeGroup" + ".enter().append('path').attr('class', 'edge')");
		writeEdgePlacement("source", curved, polar, out);
		out.endStatement();

		// Create paths for all items, and transition them to the final locations
		out.add("BrunelD3.transition(" + groupName + ".merge(added), transitionMillis)");
		writeEdgePlacement("target", curved, polar, out);
		out.endStatement();
	}

	private static void writeEdgePlacement(String target, boolean curved, boolean polar, ScriptWriter out) {
		out.addChained("attr('d', function(d) {")
				.indentMore().indentMore().onNewLine();

		if (polar) {
			out.add("var r1 = d.source.y, a1 = d.source.x, r2 = d." + target + ".y, a2 = d." + target + ".x, r = (r1+r2)/2").endStatement()
					.add("return 'M' + scale_x(r1*Math.cos(a1)) + ',' + scale_y(r1*Math.sin(a1)) +");

			// Add curve if requested, else just a straight line
			if (curved) {
				out.ln().indent().add("'Q' +  scale_x(r*Math.cos(a2)) + ',' + scale_y(r*Math.sin(a2)) + ' '");
			} else {
				out.ln().indent().add("'L'");
			}

			out.ln().indent().add(" +  scale_x(r2*Math.cos(a2)) + ',' + scale_y(r2*Math.sin(a2))")
					.endStatement();
		} else {
			out.add("var p = BrunelD3.insetEdge(scale_x(d.source.y), scale_y(d.source.x), d.source,").ln().indent().add("scale_x(d.target.y), scale_y(d.target.x), d.target)")
					.endStatement()
					.add("return 'M' + p.x1 + ',' + p.y1 + ");
			// Add curve if requested, else just a straight line
			if (curved) {
				out.ln().indent().add("'C' + (p.x1+p.x2)/2 + ',' + p.y1").ln().indent().add(" + ' ' + (p.x1+p.x2)/2 + ',' + p.y2 + ' ' ");
			} else {
				out.ln().indent().add("'L'");
			}

			out.ln().indent().add("+ p.x2 + ',' + p.y2").endStatement();
		}
		out.indentLess().indentLess().add("})");

	}
	public final boolean curved;        // True if we want a curved arc
	private final boolean arrow;        // True if we want arrows
	private final boolean polar;        // True if we have a polar layout

	DependentEdge(ElementStructure structure) {
		super(structure);
		String symbol = structure.styleSymbol;
		if (symbol == null) {
			this.arrow = true;
			this.curved = structure.chart.diagram != VisTypes.Diagram.network;
		} else {
			this.arrow = symbol.toLowerCase().contains("arrow");
			this.curved = symbol.toLowerCase().contains("curved") || symbol.toLowerCase().contains("arc");
		}
		this.polar = structure.chart.coordinates.isPolar();
	}

	public String getRowKeyFunction() {
		return "function(d) { return d.key }";
	}

	public String getStyleClasses() {
		return "'diagram hierarchy edge'";
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, ElementRepresentation.curvedPath, "edge", "graph.links");
	}

	public void writeDataStructures(ScriptWriter out) {
		// Nothing to be done
	}

	public void writeDiagramEnter(ElementDetails details, ScriptWriter out) {
		if (arrow) out.addChained("attr('marker-end', 'url(#arrow)')");
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		writeEdgePlacement("target", curved, polar, out);
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public void writePerChartDefinitions(ScriptWriter out) {
		// ensure an arrowhead is defined
		if (arrow)
			out.add("vis.append('svg:defs').selectAll('marker').data(['arrow']).enter()")
					.addChained("append('svg:marker').attr('id', 'arrow')")
					.addChained("attr('viewBox', '0 -6 10 10').attr('orient', 'auto')")
					.addChained("attr('refX', 7).attr('refY', 0)")
					.addChained("attr('markerWidth', 6).attr('markerHeight', 6)")
					.addChained("attr('fill', '#888888')")
					.addChained("append('svg:path').attr('d', 'M0,-4L8,0L0,4')")
					.endStatement();

	}

}
