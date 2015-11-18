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
import org.brunel.data.Dataset;
import org.brunel.maps.Rect;
import org.brunel.model.VisSingle;

public class GeoMapLabels extends D3Diagram {

    private final PositionFields positions;

    public GeoMapLabels(VisSingle vis, Dataset data, PositionFields positions, ScriptWriter out) {
        super(vis, data, out);
        this.positions = positions;
    }

    public ElementDetails writeDataConstruction() {

        Rect r = positions.getAllPoints().bounds();


        out.add("var geo_labels = [ {c:[31.25, 30.05], key:'cairo'}, {c:[15,15],key:'test-b'}]").endStatement();
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
