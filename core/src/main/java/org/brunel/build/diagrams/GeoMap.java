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

package org.brunel.build.diagrams;

import static org.brunel.model.VisTypes.Element.point;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.brunel.action.Param;
import org.brunel.build.LabelBuilder;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuildUtil;
import org.brunel.build.util.ContentReader;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.geom.Rect;
import org.brunel.maps.GeoInformation;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisTypes.Element;

public class GeoMap extends D3Diagram {

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
			String source = Data.quote(out.options.locMaps + "/" + out.options.version + "/" + map.getQuality() + "/" + fileName + ".json");
			out.onNewLine().add(source, ": {").indentMore();
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
	
	private static void writeCustomMapping(ScriptWriter out, String location) {
		out.add("{ ", out.quote(location), ": {} }");

	}

	private static void writeProjection(ScriptWriter out, GeoInformation geo) {

		String[] projectionDescription = geo.d3Definition().split("\n");

		// Define the projection
		out.add("var base = ").indentMore();
		for (String p : projectionDescription) out.add(p.trim()).onNewLine();
		out.indentLess();

		// Define a scaled version of the projection
		out.add("function projection(p) {")
				.indentMore().indentMore().onNewLine()
				.add("var q = base(p), t = d3.zoomTransform(zoomNode)").endStatement()
				.add("return q ? [t.k*q[0]+t.x, t.k*q[1]+t.y] : null").endStatement()
				.indentLess().indentLess().add("}").endStatement();
		out.add("function project_center(v) { return (v ? projection([v.c, v.d]) : null) || [-9e6, -9e6] }")
				.endStatement();

		out.add("var path = d3.geoPath().projection(BrunelD3.geoStream(projection))").endStatement();

		// Define axes if desired
		if (geo.withGraticule) {
			// define the steps for each line of latitude or longitude
			double step = Math.min(geo.bounds().width(), geo.bounds().height()) / 5;

			if (step < 0.1) step = Math.round(step * 200) / 200.0;
			if (step < 1) step = Math.round(step * 20) / 20.0;
			else if (step < 10) step = Math.round(step * 2) / 2.0;
			else step = 10;

			out.onNewLine().add("var graticule = d3.geoGraticule().step([", step, ",", step, "])(),")
					.ln().indent().add("graticulePath = interior.append('path').attr('class', 'grid')").endStatement()
					.onNewLine().add("function buildAxes() { graticulePath.attr('d', path(graticule) ) }");
		}

	}
	private final GeoMapping mapping;           // Mapping of identifiers to features
	private final String[] customMapParts;		// If custom map, [location, topojson field property name that matches the data]
	
	public GeoMap(ElementStructure structure) {
		super(structure);
		this.mapping = structure.geo;
		this.customMapParts = prepareCustomMap();
		if (mapping == null)
			throw new IllegalStateException("Maps need either a position field or key with the feature names; or another element to define positions");
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Read in the feature data and call build again when done");
		writeFeatureHookup(mapping, GeoInformation.getIDField(vis), out);
	}

	public ElementDetails makeDetails() {
		if (vis.tElement == point || vis.tElement == Element.text) {
			return ElementDetails.makeForCoordinates(structure);
		} else {
			// The labeling will be defined later and then used when we do the actual layout call to define the D3 data
			return ElementDetails.makeForDiagram(structure, ElementRepresentation.geoFeature, "polygon", "data._rows");
		}
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
	}

	public void writeDiagramEnter(ElementDetails details, ScriptWriter out) {
		out.addChained("classed('nondata', function(d) {return !d || d.row == null})");
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}

	public void writePerChartDefinitions(ScriptWriter out) {
		out.titleComment("Projection");
			
		writeProjection(out, mapping.getGeoInformation());

	}
	
	//Store custom map parameters and update the map bounds & projection based on the custom map.
	private String[] prepareCustomMap() {
		Param[] p = vis.tDiagramParameters;
		if (p == null || p.length != 1) return null;
		String s = p[0].asString();
		String[] parts = s.split("#");
		if (parts.length == 2) {
			Rect bounds = ContentReader.extractBoundsFromTopoJson(parts[0]);
			mapping.getGeoInformation().setProjectionBounds(bounds);

			return parts;
		}
		return null;
	}
	

	private void writeFeatureHookup(GeoMapping mapping, String idField, ScriptWriter out) {
		if (mapping.fileCount() == 0) throw new IllegalStateException("No suitable map found");
		String customFeatureName = "null";
		
		out.add("var features = ");
		if (customMapParts == null) 
			writeMapping(out, mapping);
		else {
			writeCustomMapping(out, customMapParts[0]);
			customFeatureName = "\""+ customMapParts[1] + "\"";   
		}
		
		out.endStatement();

		String idName = idField == null ? "null" : "data." + BuildUtil.canonicalFieldName(idField);
		out.add("if (BrunelD3.addFeatures(data, features,", customFeatureName, ",", idName, ", this, transitionMillis)) return").endStatement();
	}

}
