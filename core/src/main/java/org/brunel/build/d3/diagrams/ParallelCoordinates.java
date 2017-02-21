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

package org.brunel.build.d3.diagrams;

import org.brunel.action.Param;
import org.brunel.build.d3.AxisDetails;
import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.ScalePurpose;
import org.brunel.build.d3.element.ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.style.StyleTarget;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ParallelCoordinates extends D3Diagram {

    private final Set<String> TRANSFORMS = new HashSet<>(Arrays.asList("linear", "log", "root"));

    private final Field[] fields;               // The fields in the table
    private final D3ScaleBuilder builder;

    private final AxisDetails[] axes;           // Details of the axes
    private final Padding padding;              // Space around the edges
    private final double smoothness;            // 0 == linear, 1 is very smooth

    public ParallelCoordinates(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(structure, data, interaction, out);
        fields = data.fieldArray(vis.positionFields());
        builder = new D3ScaleBuilder(structure.chart, out);
        axes = makeAxisDetails(structure.chart, fields);
        padding = ModelUtil.getPadding(vis, StyleTarget.makeElementTarget(null), 6);
        padding.left += axes[0].size;
        padding.bottom += 15;       // For the bottom "axis" of titles

        // Get the smoothness from the parameter
        smoothness = vis.tDiagramParameters.length == 0 ? 0 : vis.tDiagramParameters[0].asDouble();
    }

    private static AxisDetails[] makeAxisDetails(ChartStructure chart, Field[] fields) {
        AxisDetails[] axes = new AxisDetails[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            // Create the Axis details and lay out in vertical space
            AxisDetails details = new AxisDetails("y" + i, new Field[]{f}, f.preferCategorical(), null, 9999, false);
            details.setTextDetails(chart, false);
            details.layoutVertically(chart.chartHeight);
            axes[i] = details;
        }
        return axes;
    }

    public void writeDataStructures() {
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

    public ElementDetails makeDetails(String commonSymbol) {
        return ElementDetails.makeForDiagram(vis, ElementRepresentation.generalPath, "path", "data._rows");
    }

    public void writeDiagramUpdate(ElementDetails details) {
        out.addChained("attr('d', path)");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
    }

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public void writePerChartDefinitions() {
        out.add("var parallel;").at(50).comment("Structure to store parallel axes");
    }


    public void preBuildDefinitions() {
        out.add("var rangeVertical = [geom.inner_height -", padding.vertical() + ", " + padding.top + "];")
                .at(50).comment("vertical range");

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
            String positionExpression = D3Util.writeCall(f);
            if (f.isBinned()) positionExpression += ".mid";                                     // Midpoint of bins
            out.onNewLine().add("y : function(d) { return this.scale(" + positionExpression + ") },");
            out.onNewLine().add("axis : d3.axisLeft(), numeric: " + f.isNumeric());
            out.onNewLine().indentLess().add("}");
        }
        out.indentLess().add("]").endStatement();

        out.onNewLine().ln().add("function path(d) {").indentMore().ln();
        out.add("var p = d3.path()").endStatement();
        if (smoothness == 0) defineLinearPath();
        else defineSmoothPath(smoothness);
        out.add("return p");
        out.indentLess().onNewLine().add("}").endStatement();

    }

    private void defineLinearPath() {
        out.add("parallel.forEach(function(dim, i) {").indentMore().indentMore().onNewLine()
                .add("if (i) p.lineTo(scale_x(i), dim.y(d))").endStatement()
                .add("else   p.moveTo(scale_x(i), dim.y(d))").endStatement()
                .indentLess().indentLess().add("} )").endStatement();
    }

    /**
     * The parameter passed in helps define how smooth the path is
     *
     * @param r zero-one parameter where 0 is linear and 1 is a flat curve
     */
    private void defineSmoothPath(double r) {
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
        boolean reversed = !field.preferCategorical();
        for (Param p : vis.fX) if (requestsReverse(field, p)) reversed = !reversed;
        for (Param p : vis.fY) if (requestsReverse(field, p)) reversed = !reversed;
        return reversed;
    }

    private boolean requestsReverse(Field field, Param p) {
        return p.asField().equals(field.name) && p.hasModifierOption("reverse");
    }
}
