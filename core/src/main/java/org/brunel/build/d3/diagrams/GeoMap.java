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
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.maps.GeoAnalysis;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisSingle;

class GeoMap extends D3Diagram {

    public GeoMap(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
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

        out.add("var path = d3.geo.path()").endStatement();
        out.add("path.projection()")
                .addChained("translate([geom.inner_width/2,geom.inner_height/2])")
                .addChained("scale(1.2*geom.inner_width)")
                .endStatement();
        out.comment("Read in the feature data and call build again when done");
        out.add("if (!data._features) {").indentMore().ln();
        writeFeatureHookup(x);
        out.onNewLine().add("return;");
        out.indentLess().onNewLine().add("}").endStatement();

        out.comment("Add feature geometry to each row");
        out.add("for (var i=0;i<data._rows.length; i++) {").indentMore().ln();
        out.add("var row = data._rows[i],")
                .add("feature = data._features[ data.").add(D3Util.canonicalFieldName(x)).add("(row) ]").endStatement();
        out.add("if (feature) {row.geometry = feature.geometry; row.type = feature.type}").ln();
        out.add("else data._rows.splice(i--, 1)").endStatement();
        out.indentLess().onNewLine().add("}").endStatement();

        // The labeling will be defined later and then used when we do the actual layout call to define the D3 data
        return ElementDetails.makeForDiagram("data._rows", "path", "polygon", "path", false);

    }

    private void writeFeatureHookup(String x) {
        Field key = data.field(x);
        GeoMapping mapping = GeoAnalysis.instance().make(key.categories());
        if (mapping.files.length == 0) throw new IllegalStateException("No suitable map found");
        if (mapping.files.length > 1) throw new IllegalStateException("Multiple sources not yet implemented");
        out.add("var geomapping = ");
        GeoAnalysis.writeMapping(out, mapping);
        out.endStatement();
        out.add("var that = this").endStatement();
        out.add("d3.json('http://brunelvis.org/geo/0.7/" + mapping.files[0] + ".json', function (error, x) {");
        out.indentMore().ln();
        out.add("var i, fmap={}, all = x.features").endStatement();
        out.add("for (i=0;i<all.length; i++) fmap[all[i].properties.a] = all[i]").endStatement();
        out.add("data._features = {};").at(40).comment("Map from names to features");
        out.add("for (i in geomapping) data._features[i] = fmap[geomapping[i]]").endStatement();
        out.onNewLine().add("that.build(transitionMillis);").comment("When data ready, build again");
        out.indentLess().add("})").endStatement();

    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        // Set the given location using the transform
        out.addChained("attr('d', path )").endStatement();
        addAestheticsAndTooltips(details, true);
    }

}
