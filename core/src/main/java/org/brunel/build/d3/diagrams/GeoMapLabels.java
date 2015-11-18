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

import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.ElementDefinition;
import org.brunel.build.util.ElementDetails;
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

    private final D3ScaleBuilder scales;

    public GeoMapLabels(VisSingle vis, Dataset data, D3ScaleBuilder scales, ScriptWriter out) {
        super(vis, data, out);
        this.scales = scales;
    }

    public ElementDetails writeDataConstruction() {

        Rect r1 = scales.getGeoBounds();
        Rect r2 = scales.positionFields.getAllPoints().bounds();
        Rect r = r1 == null ? r2 : r1.union(r2);

        List<LabelPoint> points = GeoAnalysis.instance().getLabelsWithin(r);
        if (points.size() > 50) points = points.subList(0, 50);

        int popHigh = points.get(0).size;
        int popLow = points.get(points.size()-1).size;

        out.add("var geo_labels = [").indentMore();
        boolean first = true;
        for (LabelPoint p : points) {
            if (!first) out.add(", ");
            String s = "{c:[" + F.format(p.x) + "," + F.format(p.y) + "], key:"
                    + Data.quote(p.label) + ", r:" + radiusFor(p, popHigh, popLow)
                    + "}";
            if (out.currentColumn() + s.length() > 100)
                out.onNewLine();
            out.add(s);
            first = false;
        }
        out.indentLess().add("]").endStatement();

        return ElementDetails.makeForDiagram("geo_labels", "circle", "point", "box", false);
    }

    private int radiusFor(LabelPoint p, int high, int low) {
        return (int) (Math.round( (p.size - low) * 3.0 / (high-low)) + 2);
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('r', function(d) { return d.r + 'px'})");
        addPositionTransform();
        out.endStatement();

        // Labels
        out.add("var labelSel = diagramLabels.selectAll('*').data(d3Data, function(d) { return d.key})").endStatement();
        out.add("labelSel.enter().append('text').attr('class','map element label')")
                .addChained("attr('dy', '0.3em')")
                .addChained("attr('dx', function(d) { return (d.r + 3) + 'px'})")
                .addChained("text(function(d) {return d.key})").endStatement();
        out.add("labelSel");
        addPositionTransform();
        out.endStatement();

    }

    private void addPositionTransform() {
        out.addChained("attr('transform', function(d) {").indentMore().indentMore()
                .onNewLine().add("var p = projection(d.c);")
                .onNewLine().add("return 'translate(' + p[0] + ' ' + p[1] + ')'")
                .onNewLine().add("})").indentLess().indentLess();
    }

    public void writeDiagramEnter() {
        out.addChained("classed('map', true)");
        out.endStatement();
    }
}
