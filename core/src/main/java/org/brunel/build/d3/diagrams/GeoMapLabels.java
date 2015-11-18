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
import org.brunel.maps.GeoProjection;
import org.brunel.maps.LabelPoint;
import org.brunel.maps.Rect;
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
        points = thin(points, r);

        int popHigh = 0, popLow = 100;
        for (LabelPoint p : points) {
            popHigh = Math.max(popHigh, p.size);
            popLow = Math.min(popLow, p.size);
        }

        out.add("var geo_labels = [").indentMore();
        boolean first = true;
        for (LabelPoint p : points) {
            if (!first) out.add(", ");
            String s = "{c:[" + F.format(p.x) + "," + F.format(p.y) + "], key:"
                    + Data.quote(p.label) + ", r:" + radiusFor(p, popHigh, popLow)
                    + ", t:" + p.type + "}";
            if (out.currentColumn() + s.length() > 100)
                out.onNewLine();
            out.add(s);
            first = false;
        }
        out.indentLess().add("]").endStatement();

        return ElementDetails.makeForDiagram("geo_labels", "path", "point", "box", false);
    }

    // we will remove points which seem likely to overlap
    private List<LabelPoint> thin(List<LabelPoint> points, Rect totalBounds) {

        Rect bds = GeoProjection.MERCATOR.projectedExtent(totalBounds);
        double scale = Math.min(800 / bds.width(), 600 / bds.height());

        ArrayList<LabelPoint> result = new ArrayList<LabelPoint>();
        ArrayList<Rect> accepted = new ArrayList<Rect>();

        Font font = new Font("Helvetica", Font.PLAIN, 12);
        FontRenderContext frc = new FontRenderContext(null, true, true);

        for (LabelPoint p : points) {
            boolean intersects = false;

            org.brunel.maps.Point pp = GeoProjection.MERCATOR.transform(p);
            double x = pp.x * scale, y = pp.y * scale;
            Rectangle2D size = font.getStringBounds(p.label, frc);
            Rect s = new Rect(x - 15, x + size.getWidth() + 5, y, y + size.getHeight());

            for (Rect r : accepted) {
                if (r.intersects(s)) {
                    System.out.println(p + " intersects " + result.get(accepted.indexOf(r)));
                    intersects = true;
                    break;
                }
            }
            if (!intersects) {
                accepted.add(s);
                result.add(p);
            }
            if (result.size() >= 60) break;
        }
        return result;
    }

    private int radiusFor(LabelPoint p, int high, int low) {
        return (int) (Math.round((p.size - low) * 4.0 / (high - low) + 3));
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('d', function(d) { return BrunelD3.symbol(['star','star','square','circle'][d.t-1], d.r)})")
                .addChained("attr('class', function(d) { return 'mark L' + d.t })");
        addPositionTransform();
        out.endStatement();

        // Labels
        out.add("diagramLabels.classed('map', true)").endStatement();
        out.add("var labelSel = diagramLabels.selectAll('*').data(d3Data, function(d) { return d.key})").endStatement();
        out.add("labelSel.enter().append('text')")
                .addChained("attr('dy', '0.3em')")
                .addChained("attr('dx', function(d) { return (d.r*1.5) + 'px'})")
                .addChained("attr('class', function(d) { return 'label L' + d.t })")
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
