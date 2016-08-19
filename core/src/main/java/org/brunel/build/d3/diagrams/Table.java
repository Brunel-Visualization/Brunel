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

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleTarget;

class Table extends D3Diagram {

    private final String[] fields;              // The fields in the table
    private final Dataset data;
    private final double[] fraction;            // The proportion of space for the column
    private final double fontSize;              // Font size for labels
    private final Padding padding;              // Padding for labels

    public Table(VisSingle vis, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(vis, data, interaction, out);
        fields = vis.positionFields();
        this.data = data;
        Field[] dataFields = vis.getDataset().fieldArray(fields);
        fraction = divideColumnSpace(dataFields);
        StyleTarget styleTarget = StyleTarget.makeElementTarget("text");
        fontSize = ModelUtil.getFontSize(vis, styleTarget, 12);
        padding = ModelUtil.getPadding(vis, styleTarget, 2);
    }

    private double[] divideColumnSpace(Field[] dataFields) {
        double[] fraction = new double[fields.length];
        for (int i = 0; i < dataFields.length; i++) {
            for (int j = 0; j < dataFields[i].rowCount(); j++) {
                String s = dataFields[i].valueFormatted(j);
                if (s != null) fraction[i] = Math.max(fraction[i], s.length());
            }
        }

        double total = 0;
        for (double f : fraction) total += f;
        for (int i = 0; i < fraction.length; i++) fraction[i] /= total;
        return fraction;
    }

    public ElementDetails initializeDiagram() {

        out.add("var posX = {").indentMore().onNewLine();
        double left = 0;
        for (int i=0; i<fields.length; i++) {
            double right = left + fraction[i];
            if (i>0) out.add(", ");
            out.add(fields[i] + ": [" + left + ", " + right + "]");
            left = right;
        }
        out.indentLess().add("}").endStatement();
        out.add("var nRows = processed.rowCount() / " + fields.length + ", h = scale_y(geom.inner_height / nRows) - scale_y(0)").endStatement();

        out.add("function x(d,p) { var v = posX[data.$series(d)]; return scale_x(geom.inner_width * v[p]) }").endStatement();
        out.add("function y(d) { return scale_y((data.$row(d)-1) * geom.inner_height / nRows) }").endStatement();

        return ElementDetails.makeForDiagram(vis, ElementRepresentation.rect, "text", "data._rows");

    }

    public void writeDefinition(ElementDetails details) {
        out.addChained("attr('x', function(d) { return x(d,0) }).attr('width',function(d) { return x(d,1) -x(d,0) } )")
                .addChained("attr('y', y).attr('height', h)");
        addAestheticsAndTooltips(details);
    }

    public void writeDiagramEnter() {
//        // The cloud needs to set all this stuff up front
//        out.add("merged.style('text-anchor', 'middle').classed('label', true)")
//                .addChained("text(labeling.content)");
//        D3LabelBuilder.addFontSizeAttribute(vis, out);
//        out.endStatement();
    }
}
