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

package org.brunel.maps;

import org.brunel.action.Param;
import org.brunel.data.io.CSV;
import org.brunel.geom.Point;
import org.brunel.geom.Poly;
import org.brunel.util.MappedLists;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class reads in information needed to analyze geographic names, and provides a method to
 * build the mapping needed for a set of feature names. It is a singleton class - it holds a lot of data!
 */
class GeoData {

    private static GeoData INSTANCE;                                                         // The singleton

    /**
     * Gets the singleton instance
     *
     * @return the analysis instance to use
     */
    public static synchronized GeoData instance() {
        if (INSTANCE == null) INSTANCE = new GeoData();
        return INSTANCE;
    }

    public static String getQuality(Param[] diagramParameters) {
        for (Param p : diagramParameters)
            if (p.type() == Param.Type.option) {
                if (p.asString().equals("medium")) return "med";
                if (p.asString().equals("high")) return "high";
                if (p.asString().equals("low")) return "low";
            }
        return "med";
    }

    MappedLists<GeoFile, Object> mapFeaturesToFiles(Object[] names, Collection<Object> unmatched) {
        MappedLists<GeoFile, Object> contained = new MappedLists<>();
        for (Object s : names) {
            String key = s.toString();
            List<Feature> item = featureByName(key);
            if (item == null) {
                unmatched.add(s);
            } else {
                for (Feature i : item)
                    contained.add(i.file, new IndexedFeature(key, i.id));
            }
        }
        return contained;
    }

    private final Map<String, List<Feature>> featureMap;  // A map from names to where to find them
    private final Map<String, GeoFile> filesByName;       // A map of canonical name to file
    private final Map<String, LabelPoint> labelsByName;   // A map of canonical name to labels
    private final GeoFile[] geoFiles;                     // Feature files we can use

    private GeoData() {
        try {
            // Read in the feature information file
            InputStream is = GeoData.class.getResourceAsStream("/org/brunel/maps/geoinfo/featureFiles.txt");
            LineNumberReader rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            geoFiles = readFileDescriptions(rdr);                       // The files
            filesByName = makeFileNameMap(geoFiles);                    // So we can identify them by name

            is = GeoData.class.getResourceAsStream("/org/brunel/maps/geoinfo/featureDetails.txt");
            rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            featureMap = readFeatureDescriptions(rdr, filesByName);     // Map from features to files & ids
            rdr.close();

            // Read label file information
            is = GeoData.class.getResourceAsStream("/org/brunel/maps/geoinfo/locations.txt");
            rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            labelsByName = readLabels(rdr, filesByName);
            rdr.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        addVariantFeatureNames();

    }

    public List<Feature> featureByName(String s) {
        s = GeoNaming.canonical(s);
        List<Feature> result = featureMap.get(s);
        if (result != null) return result;
        for (String t : GeoNaming.variants(s)) {
            result = featureMap.get(t);
            if (result != null) return result;
        }
        return null;
    }

    public GeoFile[] getGeoFiles() {
        return geoFiles;
    }

    private Map<String, GeoFile> makeFileNameMap(GeoFile[] geoFiles) {
        Map<String, GeoFile> map = new HashMap<>();
        for (GeoFile s : geoFiles) {
            map.put(GeoNaming.canonical(s.name), s);
            map.put(GeoNaming.canonical(CSV.readable(s.name)), s);
        }
        return map;
    }

    // Add variants of names by normalizing removing accent marks and periods
    private void addVariantFeatureNames() {
        List<String> keys = new ArrayList<>(featureMap.keySet());
        for (String s : keys) {
            for (String t : GeoNaming.variants(s))
                if (!featureMap.containsKey(t))
                    featureMap.put(t, featureMap.get(s));
        }
    }

    /* Read the labels and add them to the appropriate geo files */
    private static Map<String, LabelPoint> readLabels(LineNumberReader rdr, Map<String, GeoFile> filesByName) throws IOException {
        Map<String, LabelPoint> result = new HashMap<>();
        while (true) {
            String line = rdr.readLine();
            if (line == null) break;
            String[] parts = line.split("\\|");

            // Make the point using the first 5 fields (name, lat, lon, population, importance)
            LabelPoint point = LabelPoint.makeFromArray(parts);
            // Add to the global list. If duplicated, ignore duplicates
            if (!result.containsKey(point.label)) result.put(point.label, point);
            // The remaining fields are the files to which the points belong
            for (int i=5; i<parts.length; i++) {
                String fileName = GeoNaming.canonical(parts[i]);
                GeoFile geoFile = filesByName.get(fileName);
                if (geoFile == null)
                    throw new NullPointerException("Cannot find geo file named: " + fileName);
                geoFile.pts.add(point);
            }
        }


        // Sort geofile labels by importance
        for (GeoFile f : filesByName.values()) Collections.sort(f.pts);

        return result;
    }

    private Map<String, List<Feature>> readFeatureDescriptions(LineNumberReader rdr, Map<String, GeoFile> filesByName) throws IOException {
        HashMap<String, List<Feature>> map = new HashMap<>();
        // Read the features
        while (true) {
            String line = rdr.readLine();
            if (line == null) break;
            String[] parts = line.split(",");
            GeoFile geoFile = filesByName.get(GeoNaming.canonical(parts[0]));
            int id = Integer.parseInt(parts[1]);
            if (geoFile == null) throw new IllegalStateException("Unknown file name: " + parts[0]);
            Feature data = new Feature(geoFile, id);
            for (int i = 2; i < parts.length; i++) {
                String name = parts[i];
                List<Feature> list = map.get(name);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(name, list);
                }
                list.add(data);
            }
        }
        return map;
    }

    private GeoFile[] readFileDescriptions(LineNumberReader rdr) throws IOException {
        // Read the names of the files and their sizes (in K)
        List<GeoFile> list = new ArrayList<>();
        while (true) {
            String[] fileLine = rdr.readLine().split("\\|");
            if (fileLine.length != 4) break;                            // End of the file definitions
            list.add(new GeoFile(fileLine[0], fileLine[1], fileLine[2]));
        }

        return list.toArray(new GeoFile[list.size()]);
    }

    /**
     * For a set of features, returns the mapping to use for them
     *
     * @param names feature names
     * @return resulting mapping
     */
    GeoMapping make(Object[] names, Param[] geoParameters) {
        return GeoMapping.createGeoMapping(names, makeRequiredFiles(geoParameters), this, GeoData.getQuality(geoParameters));
    }

    GeoMapping world() {
        return make(new Object[0], new Param[]{Param.makeString("world")});
    }

    private List<GeoFile> makeRequiredFiles(Param[] params) {
        Set<GeoFile> byFeature = new HashSet<>();
        List<GeoFile> result = new ArrayList<>();
        for (Param p : params) {
            if (p.type() == Param.Type.option) continue;
            String key = GeoNaming.canonical(p.asString());
            GeoFile f = filesByName.get(key);
            if (f != null) {
                result.add(f);
            } else {
                List<Feature> features = featureMap.get(key);
                if (features != null) {
                    byFeature.add(features.get(0).file);
                } else {
                    LabelPoint location = labelsByName.get(key);
                    if (location != null)
                        byFeature.add(smallestFileContaining(location));

                }
            }

        }
        result.addAll(byFeature);
        return result.isEmpty() ? null : result;
    }

    private GeoFile smallestFileContaining(Point point) {
        GeoFile result = null;
        for (GeoFile g : geoFiles) {
            if (g.covers(point)) {
                if (result == null || g.bounds.area() < result.bounds.area())
                    result = g;
            }
        }
        return result;
    }

    GeoMapping makeForPoints(Poly hull, Param[] geoParameters) {
        return GeoMapping.createGeoMapping(hull, makeRequiredFiles(geoParameters), this, GeoData.getQuality(geoParameters));
    }

    /* where a feature can be found */
    private static class Feature {
        final GeoFile file;
        final int id;

        private Feature(GeoFile file, int id) {
            this.file = file;
            this.id = id;
        }
    }
}
