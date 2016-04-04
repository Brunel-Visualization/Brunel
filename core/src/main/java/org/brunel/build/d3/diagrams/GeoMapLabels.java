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

import org.brunel.build.chart.ChartStructure;
import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.geom.Point;
import org.brunel.geom.Rect;
import org.brunel.maps.LabelPoint;
import org.brunel.model.VisSingle;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class GeoMapLabels extends D3Diagram {

    private final NumberFormat F = new DecimalFormat("#.####");

    private final ChartStructure structure;

    public GeoMapLabels(VisSingle vis, Dataset data, ChartStructure structure, ScriptWriter out) {
        super(vis, data, out);
        this.structure = structure;
    }

    public String getRowKey() {
        return "d[2]";
    }

    public void preBuildDefinitions() {
        List<LabelPoint> points = structure.geo.getLabelsWithinScaleBounds();

        int maxPoints = 40;
        if (vis.tDiagramParameters[0].modifiers().length > 0) {
            maxPoints = (int) vis.tDiagramParameters[0].modifiers()[0].asDouble();
        }

        points = thin(points, maxPoints);

        int popHigh = 0, popLow = 100;
        for (LabelPoint p : points) {
            popHigh = Math.max(popHigh, p.size);
            popLow = Math.min(popLow, p.size);
        }
        out.onNewLine().comment("lon, lat, label, size, type");
        out.add("var geo_labels = [").indentMore();
        boolean first = true;
        for (LabelPoint p : points) {
            if (!first) out.add(", ");
            String s = "[" + F.format(p.x) + "," + F.format(p.y) + ","
                    + Data.quote(p.label) + "," + radiusFor(p, popHigh, popLow)
                    + "," + p.type + "]";
            if (out.currentColumn() + s.length() > 120)
                out.onNewLine();
            out.add(s);
            first = false;
        }
        out.indentLess().add("]").endStatement();
    }

    public ElementDetails initializeDiagram() {
        return ElementDetails.makeForDiagram(vis, "geo_labels", ElementRepresentation.path, "point", "box", false);
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('d', function(d) { return BrunelD3.symbol(['star','star','square','circle'][d[4]-1], d[3]*geom.default_point_size/14)})")
                .addChained("attr('class', function(d) { return 'mark L' + d[4] })");
        out.addChained("attr('transform', projectTransform)");
        out.endStatement();

        // Labels
        out.add("diagramLabels.classed('map', true)").endStatement();
        out.add("var labelSel = diagramLabels.selectAll('*').data(d3Data, function(d) { return d[2]})").endStatement();
        out.add("labelSel.enter().append('text')")
                .addChained("attr('dy', '0.3em')")
                .addChained("attr('dx', function(d) { return (2 + d[3]*geom.default_point_size/10) + 'px'})")
                .addChained("attr('class', function(d) { return 'label L' + d[4] })")
                .addChained("text(function(d) {return d[2]})").endStatement();

        out.add("labelSel");
        out.addChained("attr('transform', projectTransform)");
        out.endStatement();
    }

    public boolean needsDiagramLabels() {
        return true;
    }

    public void writeDiagramEnter() {
        out.addChained("classed('map', true)");
        out.endStatement();
    }

    // we will remove points which seem likely to overlap
    private List<LabelPoint> thin(List<LabelPoint> points, int maxPoints) {

        Rect bds = structure.geo.projectedBounds();
        double scale = Math.min(800 / bds.width(), 600 / bds.height());

        ArrayList<LabelPoint> result = new ArrayList<>();
        ArrayList<Rect> accepted = new ArrayList<>();

        Font font = new Font("Helvetica", Font.PLAIN, 12);
        FontRenderContext frc = new FontRenderContext(null, true, true);

        for (LabelPoint p : points) {
            boolean intersects = false;

            Point pp = structure.geo.transform(p);
            double x = pp.x * scale, y = pp.y * scale;
            Rectangle2D size = font.getStringBounds(p.label, frc);
            Rect s = new Rect(x - 15, x + size.getWidth() + 15, y, y + size.getHeight());

            for (Rect r : accepted) {
                if (r.intersects(s)) {
                    intersects = true;
                    break;
                }
            }
            if (!intersects) {
                accepted.add(s);
                result.add(p);
            }
            if (result.size() >= maxPoints) break;
        }
        return result;
    }

    private int radiusFor(LabelPoint p, int high, int low) {
        return (int) (Math.round((p.size - low) * 4.0 / (high - low) + 3));
    }
}
