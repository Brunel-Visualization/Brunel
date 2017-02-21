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

import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.element.ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.style.StyleTarget;

class Table extends D3Diagram {

    private final double[] fraction;            // The proportion of space for the column
    private final double fontSize;              // Font size for labels
    private final Padding padding;              // Padding for labels
    private final Field[] fields;               // The fields we are showing

    public Table(ElementStructure structure, Dataset data, ScriptWriter out) {
        super(structure, data);
        fields = data.fieldArray(vis.positionFields());
        padding = ModelUtil.getPadding(vis, StyleTarget.makeElementTarget(null), 2);
        padding.top += 15;       // For the titles
        fraction = divideColumnSpace(fields);
        StyleTarget styleTarget = StyleTarget.makeElementTarget("text");
        fontSize = ModelUtil.getFontSize(vis, styleTarget, 12);
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

    public void writeDataStructures(ScriptWriter out) {
        out.onNewLine().comment("Define columns for the table");
        out.add("var L = " + padding.left + ", W = geom.inner_width - " + padding.horizontal()).endStatement();
        out.add("var columns = [").onNewLine().indentMore();
        double pos = 0;
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            if (i > 0) out.add(",").onNewLine();
            out.add("{ label: " + Data.quote(f.label))
                    .add(", value: data." + D3Util.canonicalFieldName(f.name) + "_f")
                    .add(", ext: [L + W * " + Data.format(pos, false) + ", L + W * " + Data.format(pos+fraction[i], false) + "]")
                    .add(" }");
            pos += fraction[i];
        }
        out.indentLess().add("]").endStatement();

    }

    public ElementDetails makeDetails() {
        return ElementDetails.makeForDiagram(structure, ElementRepresentation.rect, "rect", "data._rows");
    }

    public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
        out.addChained("attr('x', L).attr('width', W)")
                .addChained("attr('y', function(d,i) { return " + padding.top + " + " + fontSize + " * i }).attr('height', " + fontSize + ")");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

}
