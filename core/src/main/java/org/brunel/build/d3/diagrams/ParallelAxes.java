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
import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.ScalePurpose;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleTarget;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ParallelAxes extends D3Diagram {

    private final Set<String> TRANSFORMS = new HashSet<>(Arrays.asList("linear", "log", "root"));

    private final Field[] fields;               // The fields in the table
    private final D3ScaleBuilder builder;

    private final AxisDetails[] axes;           // Details of the axes
    private final Padding padding;              // Space around the edges

    public ParallelAxes(VisSingle vis, Dataset data, D3Interaction interaction, ElementStructure structure, ScriptWriter out) {
        super(vis, data, interaction, out);
        fields = vis.getDataset().fieldArray(vis.positionFields());
        builder = new D3ScaleBuilder(structure.chart, out);
        axes = makeAxisDetails(structure.chart, fields);

        padding = ModelUtil.getPadding(vis, StyleTarget.makeElementTarget(null), 6);
        padding.left += axes[0].size;
        padding.bottom += 15;       // For the bottom "axis" of titles
    }

    private static AxisDetails[] makeAxisDetails(ChartStructure chart, Field[] fields) {
        AxisDetails[] axes = new AxisDetails[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            String name = "dim" + (i + 1);
            // Create the Axis details and lay out in vertical space
            AxisDetails details = new AxisDetails(name, new Field[]{f}, f.preferCategorical(), null, 9999, false);
            details.setTextDetails(chart, false);
            details.layoutVertically(chart.chartHeight);
            axes[i] = details;
        }
        return axes;

    }

    public ElementDetails initializeDiagram() {
        // One time only, create the axes
        out.add("if (!axes.length) {").indentMore().onNewLine();
        writeAxesCreation();
        out.onNewLine().onNewLine().add("}").onNewLine();

        // Write the calls to display the axes
        writeAxesCalls();

        return ElementDetails.makeForDiagram(vis, ElementRepresentation.generalPath, "path", "data._rows");
    }

    private void writeAxesCreation() {

        int N = fields.length;

        for (int i = 0; i < N; i++) {
            String name = "dim" + (i + 1);

            out.onNewLine().add("interior.append('g').attr('class', 'axis " + name + "')")
                    .addChained("attr('transform','translate(' + scale_x(" + i + ") + ',0)')");
            out.endStatement();

            builder.defineAxis("axes[" + i + "] = d3.axisLeft", axes[i], false);
        }

    }

    private void writeAxesCalls() {

        out.add("BrunelD3.transition(interior.selectAll('g.axis'), transitionMillis)")
                .addChained("each(function(d,i) { d3.select(this).call(axes[i].scale(axes[i].scale())) })")
                .endStatement();

    }

    public void writeDefinition(ElementDetails details) {
//        out.addChained("attr('x', function(d) { return x(d,0) }).attr('width',function(d) { return x(d,1) -x(d,0) } )")
//                .addChained("attr('y', y).attr('height', h)");
        addAestheticsAndTooltips(details);
    }

    public void preBuildDefinitions() {
        out.add("var rangeVertical = [", padding.top, ", geom.inner_height -", padding.vertical() + "];")
                .at(50).comment("vertical range");

        out.add("var scale_x = d3.scaleLinear().range(["
                + padding.left + ", geom.inner_width -", padding.horizontal() + "])")
                .addChained("domain([0,", fields.length - 1, "])")
                .endStatement();

        out.comment("Define the individual scales");
        for (int i = 0; i < fields.length; i++) {
            out.add("var scale_dim" + (i + 1) + " = ");
            builder.defineScaleWithDomain(null, new Field[]{fields[i]}, ScalePurpose.parallel, 2, getTransform(fields[i]), null, isReversed(fields[i]));
            out.addChained("range(rangeVertical)");
            out.endStatement();
        }
        out.add("var axes = [];").at(50).comment("array of all axes");

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
        if (p.asField().equals(field.name))
            for (Param q : p.modifiers())
                if (q.asString().equals("reverse")) return true;
        return false;
    }

    public void writeDiagramEnter() {
//        // The cloud needs to set all this stuff up front
//        out.add("merged.style('text-anchor', 'middle').classed('label', true)")
//                .addChained("text(labeling.content)");
//        D3LabelBuilder.addFontSizeAttribute(vis, out);
//        out.endStatement();
    }
}
