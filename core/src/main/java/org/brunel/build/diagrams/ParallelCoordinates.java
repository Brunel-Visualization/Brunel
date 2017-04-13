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

package org.brunel.build.diagrams;

import org.brunel.action.Param;
import org.brunel.build.LabelBuilder;
import org.brunel.build.ScaleBuilder;
import org.brunel.build.ScalePurpose;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.guides.AxisDetails;
import org.brunel.build.guides.AxisRequirement;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuildUtil;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.model.VisTypes;
import org.brunel.model.style.StyleTarget;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ParallelCoordinates extends D3Diagram {

	private static AxisDetails[] makeAxisDetails(ChartStructure chart, Field[] fields) {
		AxisDetails[] axes = new AxisDetails[fields.length];
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			// Create the Axis details and lay out in vertical space
			AxisRequirement req = new AxisRequirement(VisTypes.Axes.y, i);
			AxisDetails details = new AxisDetails(req, new Field[]{f}, f.preferCategorical());
			details.setTextDetails(chart, false);
			details.layoutVertically(chart.location.height);
			axes[i] = details;
		}
		return axes;
	}

	private final Set<String> TRANSFORMS = new HashSet<>(Arrays.asList("linear", "log", "root"));
	private final Field[] fields;               // The fields in the table
	private final Padding padding;              // Space around the edges
	private final double smoothness;            // 0 == linear, 1 is very smooth

	public ParallelCoordinates(ElementStructure structure) {
		super(structure);
		fields = structure.data.fieldArray(vis.positionFields());
		AxisDetails[] axes = makeAxisDetails(structure.chart, fields);
		padding = ModelUtil.getPadding(vis, StyleTarget.makeElementTarget(null), 6);
		padding.left += axes[0].size;
		padding.bottom += 15;       // For the bottom "axis" of titles

		// Get the smoothness from the parameter
		smoothness = vis.tDiagramParameters.length == 0 ? 0 : vis.tDiagramParameters[0].asDouble();
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, ElementRepresentation.generalPath, "path", "data._rows");
	}

	public void preBuildDefinitions(ScriptWriter out) {

		ScaleBuilder builder = new ScaleBuilder(structure.chart, out);

		out.add("var rangeVertical = [geom.inner_height -", padding.vertical() + ", " + padding.top + "];")
				.comment("vertical range");

		out.add("var scale_x = d3.scaleLinear().range(["
				+ padding.left + ", geom.inner_width -", padding.horizontal() + "])")
				.addChained("domain([0,", fields.length - 1, "])")
				.endStatement();

		out.onNewLine().ln().comment("Define data structures for parallel axes");

		out.add("parallel = [").onNewLine().indentMore();
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (i > 0) out.add(",").onNewLine();
			out.add("{").indentMore()
					.onNewLine().add("label : " + Data.quote(f.label) + ",")
					.onNewLine().add("scale : ");
			builder.defineScaleWithDomain(null, new Field[]{f}, ScalePurpose.parallel, 2, getTransform(f), null, isReversed(f));
			if (out.currentColumn() > 60)
				out.addChained("range(rangeVertical),");
			else
				out.add(".range(rangeVertical),");
			String positionExpression = BuildUtil.writeCall(f);
			if (f.isBinned()) positionExpression += ".mid";                                     // Midpoint of bins
			out.onNewLine().add("y : function(d) { return this.scale(" + positionExpression + ") },");
			out.onNewLine().add("axis : d3.axisLeft(), numeric: " + f.isNumeric());
			out.onNewLine().indentLess().add("}");
		}
		out.indentLess().add("]").endStatement();

		out.onNewLine().ln().add("function path(d) {").indentMore().ln();
		out.add("var p = d3.path()").endStatement();
		if (smoothness == 0) defineLinearPath(out);
		else defineSmoothPath(smoothness, out);
		out.add("return p");
		out.indentLess().onNewLine().add("}").endStatement();

	}

	public void writeDataStructures(ScriptWriter out) {
		out.add("var axes = interior.selectAll('g.parallel.axis').data(parallel)").endStatement();
		out.add("var builtAxes = axes.enter().append('g')")
				.addChained("attr('class', function(d,i) { return 'parallel axis dim' + (i+1) })")
				.addChained("attr('transform', function(d,i) { return 'translate(' + scale_x(i) + ',0)' })")
				.addChained("each(function(d) {").indentMore().indentMore()
				.add("d3.select(this).append('text').attr('class', 'axis title').text(d.label)")
				.addChained("attr('x', 0).attr('y', geom.inner_height).attr('dy', '-0.3em').style('text-anchor', 'middle')")
				.indentLess().indentLess().add("})").endStatement();

		// Write the calls to display the axes

		out.add("BrunelD3.transition(axes.merge(builtAxes), transitionMillis)")
				.addChained("each(function(d,i) { d3.select(this).call(d.axis.scale(d.scale)); })")
				.endStatement();
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		out.addChained("attr('d', path)");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public void writePerChartDefinitions(ScriptWriter out) {
		out.add("var parallel;").comment("Structure to store parallel axes");
	}

	private void defineLinearPath(ScriptWriter out) {
		out.add("var radius = 5").endStatement();
		out.add("parallel.forEach(function(dim, i) {").indentMore().indentMore().onNewLine();
		if (element == VisTypes.Element.point) {
			out.add("p.moveTo(scale_x(i)+radius, dim.y(d))").endStatement()
					.add("p.arc(scale_x(i), dim.y(d), radius, 0, 2 * Math.PI)").endStatement();
		} else {
			out.add("if (i) p.lineTo(scale_x(i), dim.y(d))").endStatement()
					.add("else   p.moveTo(scale_x(i), dim.y(d))").endStatement();
		}
		out.indentLess().indentLess().add("} )").endStatement();
	}

	/**
	 * The parameter passed in helps define how smooth the path is
	 *
	 * @param r zero-one parameter where 0 is linear and 1 is a flat curve
	 */
	private void defineSmoothPath(double r, ScriptWriter out) {
		out.add("var xa, ya, xb, yb, i, xm, ym, r = ", r / 2).endStatement();
		out.add("parallel.forEach(function(dim, i) {").indentMore().indentMore().onNewLine()
				.add("xb = scale_x(i), yb = parallel[i].y(d)").endStatement()
				.add("if (i) p.bezierCurveTo(xa +(xb-xa)*r, ya, xb +(xa-xb)*r, yb, xb, yb)").endStatement()
				.add("else   p.moveTo(xb, yb)").endStatement()
				.add("xa = xb; ya = yb").endStatement()
				.indentLess().indentLess().add("} )").endStatement();
	}

	private String getTransform(Field field) {
		for (Param p : vis.fX) {
			String s = getTransform(field, p);
			if (s != null) return s;
		}
		for (Param p : vis.fY) {
			String s = getTransform(field, p);
			if (s != null) return s;
		}
		return null;
	}

	private String getTransform(Field field, Param p) {
		if (p.asField().equals(field.name))
			for (Param q : p.modifiers())
				if (TRANSFORMS.contains(q.asString()))
					return q.asString();
		return null;
	}

	private boolean isReversed(Field field) {
		// Numeric runs bottom to top
		boolean reversed = !field.isNumeric();
		for (Param p : vis.fX) if (requestsReverse(field, p)) reversed = !reversed;
		for (Param p : vis.fY) if (requestsReverse(field, p)) reversed = !reversed;
		return reversed;
	}

	private boolean requestsReverse(Field field, Param p) {
		return p.asField().equals(field.name) && p.hasModifierOption("reverse");
	}
}
