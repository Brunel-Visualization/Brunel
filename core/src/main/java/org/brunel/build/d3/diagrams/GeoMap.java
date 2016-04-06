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

import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.maps.GeoInformation;
import org.brunel.maps.GeoMapping;
import org.brunel.maps.projection.ProjectionBuilder;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Element;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class GeoMap extends D3Diagram {

    public static void writeProjection(ScriptWriter out, GeoInformation geo) {
        out.comment("Define the projection");

        // Calculate a suitable projection
        String[] projectionDescription = geo.d3Definition().split("\n");
        out.indentMore();
        out.add("var ");
        out.indentMore();

        // Define Winkel Tripel function if needed
        if (projectionDescription[0].contains("winkel3")) {
            String[] strings = ProjectionBuilder.WinkelD3Function;
            out.add("winkel3 =", strings[0]);
            out.indentMore();
            for (int i = 1; i < strings.length; i++)
                out.onNewLine().add(strings[i]);
            out.add(",").onNewLine();
            out.indentLess();

        }

        // Define the projection
        out.add("projection =", projectionDescription[0].trim());
        out.indentMore();
        for (int i = 1; i < projectionDescription.length; i++)
            out.onNewLine().add(projectionDescription[i].trim());
        out.add(",").onNewLine();
        out.indentLess();

        // Always add pan/zoom for now -- should check to see if turned off
        out.add("zoom = d3.behavior.zoom()")
                .addChained("scale(projection.scale()).translate(projection.translate())")
                .addChained("on('zoom', function() {").onNewLine();
        out.indentMore();
        out.add("projection.translate(zoom.translate()).scale(zoom.scale())").endStatement();
        out.add("updateAll(0)").endStatement();
        out.indentLess().add("})");
        out.endStatement();
        out.indentLess();
        out.add("vis.call(zoom)").endStatement();
        // Define a 'safe' version of projection which sends failed projection points off the screen
        // This occurs for the albersUSA projection ...
        out.add("function projectTransform(p) {").indentMore()
                .onNewLine().add("var q = projection(p)").endStatement()
                .onNewLine().add("if (!q) q = [-9e9,9e9]").endStatement()
                .onNewLine().add("return 'translate(' + q[0] + ',' + q[1] + ')'")
                .indentLess().onNewLine().add("}");
        out.onNewLine().add("function proj(p) { var q = p ? projection(p) : null; return q || [null,null] }").ln();

    }

    private final GeoMapping mapping;           // Mapping of identifiers to features

    public GeoMap(VisSingle vis, Dataset data, GeoMapping geo, ScriptWriter out) {
        super(vis, data, out);
        this.mapping = geo;
        if (mapping == null)
            throw new IllegalStateException("Maps need either a position field or key with the feature names; or another element to define positions");
    }

    public ElementDetails initializeDiagram() {
        out.indentLess().comment("Read in the feature data and call build again when done");
        writeFeatureHookup(mapping, GeoInformation.getIDField(vis));

        if (vis.tElement == Element.point || vis.tElement == Element.text) {
            return ElementDetails.makeForCoordinates(vis, ModelUtil.getElementSymbol(vis));
        } else {
            out.add("var path = d3.geo.path().projection(projection)").endStatement();

            // The labeling will be defined later and then used when we do the actual layout call to define the D3 data
            return ElementDetails.makeForDiagram(vis, ElementRepresentation.geoFeature, "polygon", "data._rows");
        }
    }

    private void writeFeatureHookup(GeoMapping mapping, String idField) {
        if (mapping.fileCount() == 0) throw new IllegalStateException("No suitable map found");

        out.add("var features = ");
        writeMapping(out, mapping);
        out.endStatement();

        String idName = idField == null ? "null" : "data." + D3Util.canonicalFieldName(idField);
        out.add("if (BrunelD3.addFeatures(data, features,", idName, ", this, transitionMillis)) return").endStatement();
    }

    /**
     * Given a GeoMapping, assembles the Javascript needed to use it
     *
     * @param out destination for the Javascript
     * @param map the mapping to output
     */
    public static void writeMapping(ScriptWriter out, GeoMapping map) {

        String[] files = map.getFiles();

        // Overall combined map from file name -> (Map of data name to feature index in that file)
        Map<String, Map<Object, Integer>> combined = new LinkedHashMap<>();
        for (String geo : files) combined.put(geo, new TreeMap<Object, Integer>());

        for (Entry<Object, int[]> e : map.getFeatureMap().entrySet()) {
            Object dataName = e.getKey();
            int[] indices = e.getValue();                               // [FILE INDEX, FEATURE KEY]
            String fileName = files[indices[0]];
            Map<Object, Integer> features = combined.get(fileName);
            features.put(dataName, indices[1]);
        }

        // Write out the resulting structure
        out.add("{").indentMore();
        for (int k = 0; k < files.length; k++) {
            if (k > 0) out.add(",").onNewLine();
            String fileName = files[k];
            String source = Data.quote(out.options.locMaps + "/" + out.options.version + "/" + fileName + ".json");
            out.onNewLine().add(source, ":{").indentMore();
            int i = 0;
            Map<Object, Integer> features = combined.get(fileName);
            for (Entry<Object, Integer> s : features.entrySet()) {
                if (i++ > 0) out.add(", ");
                out.addQuoted(s.getKey()).add(":").add(s.getValue());
            }
            out.indentLess().onNewLine().add("}");
        }

        out.indentLess().onNewLine().add("}");
    }

    public void writeDefinition(ElementDetails details) {
//
//        elementBuilder.writeCoordinateDefinition(details);
////        if (vis.tElement == Element.point || vis.tElement == Element.text) {
////            out.addChained("attr('transform', function(d) { return projectTransform(d.geo_properties ? [d.geo_properties.c, d.geo_properties.d]: [-999,-999]) } )");
////            definePoint(details);
////            out.endStatement();
////            addAestheticsAndTooltips(details, true);
////        } else {
////            // Set the given location using the transform
////            out.addChained("attr('d', path )").endStatement();
////        }
//        addAestheticsAndTooltips(details, true);
    }

    public void writePreDefinition(ElementDetails details) {
        out.add("selection.classed('nondata', function(d) {return !d || d.row == null})").endStatement();
    }

}
