/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.build.d3.diagrams;

import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.ElementDefinition;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.maps.GeoAnalysis;
import org.brunel.maps.GeoMapping;
import org.brunel.maps.GeoProjection;
import org.brunel.model.VisSingle;

class GeoMap extends D3Diagram {

    public GeoMap(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
    }

    public void writeDiagramEnter() {
        super.writeDiagramEnter();
    }

    public ElementDetails writeDataConstruction() {
        String x;
        if (vis.fKeys.isEmpty()) {
            if (vis.positionFields().length == 0)
                throw new IllegalStateException("Maps need either a position field or key with the feature names");
            x = vis.positionFields()[0];
        } else {
            x = vis.fKeys.get(0).asField();
        }
        Field key = data.field(x);
        GeoMapping mapping = GeoAnalysis.instance().make(key.categories());

        // Calculate a suitable projection
        GeoProjection projection = new GeoProjection("geom.inner_width", "geom.inner_height", "winkel3");
        String[] projectionDescription = projection.makeProjection(mapping.totalBounds()).split("\n");
        out.indentMore();
        out.add("var ");
        out.indentMore();
        if (projectionDescription[0].contains("winkel3")) {
            String[] strings = GeoProjection.WinkelD3Function;
            out.add("winkel3 =", strings[0]);
            for (int i = 1; i < strings.length; i++)
                out.onNewLine().add(strings[i]);
            out.add(",").onNewLine();
        }
        out.add("projection =", projectionDescription[0].trim());
        for (int i = 1; i < projectionDescription.length; i++)
            out.onNewLine().add(projectionDescription[i].trim());
        out.add(",").onNewLine()
                .add("path = d3.geo.path().projection(projection)").endStatement();
        out.indentLess();

        out.comment("Read in the feature data and call build again when done");
        writeFeatureHookup(mapping, x);

        // The labeling will be defined later and then used when we do the actual layout call to define the D3 data
        return ElementDetails.makeForDiagram("data._rows", "path", "polygon", "path", false);

    }

    private void writeFeatureHookup(GeoMapping mapping, String idField) {
        if (mapping.files.length == 0) throw new IllegalStateException("No suitable map found");
//        if (mapping.files.length > 1) throw new IllegalStateException("Multiple sources not yet implemented");

        out.add("var features = ");
        GeoAnalysis.writeMapping(out, mapping);
        out.endStatement();

        String id = D3Util.canonicalFieldName(idField);

        String source = Data.quote("http://brunelvis.org/geo/0.7/" + mapping.files[0].name + ".json");
        out.add("if (BrunelD3.addFeatures(data,", source, ", features, data." + id, ", this, transitionMillis)) return").endStatement();
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        // Set the given location using the transform
        out.addChained("attr('d', path )").endStatement();
        addAestheticsAndTooltips(details, true);
    }

    public void writePreDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.add("selection.classed('nondata', function(d) {return !d || d.row == null})").endStatement();
    }

}
