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

import org.brunel.build.d3.ElementDefinition;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.maps.GeoAnalysis;
import org.brunel.maps.LabelPoint;
import org.brunel.maps.Rect;
import org.brunel.model.VisSingle;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

public class GeoMapLabels extends D3Diagram {

    private final NumberFormat F = new DecimalFormat("#.####");

    private final PositionFields positions;

    public GeoMapLabels(VisSingle vis, Dataset data, PositionFields positions, ScriptWriter out) {
        super(vis, data, out);
        this.positions = positions;
    }

    public ElementDetails writeDataConstruction() {

        Rect r = positions.getAllPoints().bounds();
        List<LabelPoint> points = GeoAnalysis.instance().getLabelsWithin(r);
        if (points.size() > 50) points = points.subList(0,50);
        out.add("var geo_labels = [").indentMore();
        boolean first = true;
        for (LabelPoint p : points) {
            if (!first) out.add(", ");
            String s = "{c:[" + F.format(p.x) + "," + F.format(p.y) +"], key:" + Data.quote(p.label) + "}";
            if (out.currentColumn() + s.length() > 100)
                out.onNewLine();
            out.add(s);
            first = false;
        }
        out.indentLess().add("]").endStatement();

        return ElementDetails.makeForDiagram("geo_labels", "text", "text", "box", false);
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('transform', function(d) {").indentMore()
                .onNewLine().add("var p = projection(d.c);")
                .onNewLine().add("return 'translate(' + p[0] + ' ' + p[1] + ')'")
                .onNewLine().add("})")
                .indentLess().endStatement();

    }

    public void writeDiagramEnter() {
        // The cloud needs to set all this stuff up front
        out.addChained("attr('dy', '0.3em').style('text-anchor', 'middle').classed('label', true)")
                .addChained("text(function(d) {return d.key} )");
        labelBuilder.addFontSizeAttribute(vis);
        out.endStatement();
    }
}
