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
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.maps.LabelPoint;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

public class GeoMapLabels extends D3Diagram {

    private final NumberFormat F = new DecimalFormat("#.####");

    public GeoMapLabels(ElementStructure vis, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(vis, data, interaction, out);
    }

    public String getRowKeyFunction() {
        return "function(d) { return d[2] }";
    }

    public void preBuildDefinitions() {
        List<LabelPoint> all = structure.chart.geo.getLabelsForFiles();

        int maxPoints = 40;
        if (vis.tDiagramParameters[0].modifiers().length > 0) {
            maxPoints = (int) vis.tDiagramParameters[0].modifiers()[0].asDouble();
        }

        List<LabelPoint> pointsMax = subset(all, maxPoints);

        // Get the exact right number we want
        List<LabelPoint> points = pointsMax.size() <= maxPoints
                ? pointsMax : pointsMax.subList(0, maxPoints);

        int popHigh = 0, popLow = 100;
        for (LabelPoint p : points) {
            popHigh = Math.max(popHigh, p.pop);
            popLow = Math.min(popLow, p.pop);
        }

        out.onNewLine().comment("lon, lat, label, size, type");
        out.add("var geo_labels = [").indentMore();
        boolean first = true;

        for (LabelPoint p : points) {
            if (!first) out.add(", ");
            String s = "[" + F.format(p.x) + "," + F.format(p.y) + ","
                    + Data.quote(p.label) + "," + radiusFor(p, popHigh, popLow)
                    + "," + (5 - p.importance) + "]";
            if (out.currentColumn() + s.length() > 120)
                out.onNewLine();
            out.add(s);
            first = false;
        }
        out.indentLess().add("]").endStatement();
    }

    private List<LabelPoint> subset(List<LabelPoint> all, int maxPoints) {
        return maxPoints < all.size() ? all.subList(0, maxPoints) : all;
    }

    public ElementDetails initializeDiagram() {
        return ElementDetails.makeForDiagram(vis, ElementRepresentation.symbol, "point", "geo_labels");
    }

    public void writeDiagramUpdate(ElementDetails details) {
        out.addChained("attr('d', function(d) { return BrunelD3.symbol(d[4] == 0 ? 'star' : (d[4] == 1 ? 'square' : 'circle'), d[3]*geom.default_point_size/14)})")
                .addChained("attr('class', function(d) { return 'element mark L' + d[4] })");
        out.addChained("attr('transform', function(d) {")
                .indentMore().indentMore().onNewLine()
                .add("var p = projection(d)").endStatement()
                .add("return p && 'translate(' + p[0] + ', ' + p[1] + ')'")
                .indentLess().indentLess().onNewLine().add("} )")
                .endStatement();

        out.add("labels.classed('map', true)").endStatement();

        // Labels
        out.add("var labeling = {").indentMore()
                .onNewLine().add("method:'box', pad:3, inside:false, align:'start', granularity:2,")
                .onNewLine().add("location:['right', 'middle'], content: function(d) {return d[2]}")
                .indentLess().onNewLine().add("}").endStatement();

        out.add("BrunelD3.label(selection, labels, labeling, 0, geom)").endStatement();
    }


    public void writeDiagramEnter(ElementDetails details) {
        out.addChained("classed('map', true)");
    }

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		// Do nothing; this diagram is nothing but labels
	}

	private int radiusFor(LabelPoint p, int high, int low) {
        return (int) (Math.round((p.pop - low) * 4.0 / (high - low) + 3));
    }
}
