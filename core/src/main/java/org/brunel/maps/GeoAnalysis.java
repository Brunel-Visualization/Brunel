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
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * This class reads in information needed to analyze geographic names, and provides a method to
 * build the mapping needed for a set of feature names.
 */
public class GeoAnalysis {

    private static final Pattern PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+"); // Removes diacretics
    private static GeoAnalysis INSTANCE;                                                         // The singleton

    /**
     * Gets the singleton instance
     *
     * @return the analysis instance to use
     */
    public static synchronized GeoAnalysis instance() {
        if (INSTANCE == null) INSTANCE = new GeoAnalysis();
        return INSTANCE;
    }

    /**
     * Given a GeoMapping, assembles the Javascript needed to use it
     *
     * @param out destination for the Javascript
     * @param map the mapping to output
     */
    public static void writeMapping(ScriptWriter out, GeoMapping map) {

        // Overall combined map from file name -> (Map of data name to feature index in that file)
        Map<String, Map<Object, Integer>> combined = new LinkedHashMap<String, Map<Object, Integer>>();
        for (GeoFile geo : map.getFiles()) combined.put(geo.name, new TreeMap<Object, Integer>());

        for (Map.Entry<Object, int[]> e : map.getFeatureMap().entrySet()) {
            Object dataName = e.getKey();
            int[] indices = e.getValue();                               // [FILE INDEX, FEATURE KEY]
            String fileName = map.getFiles()[indices[0]].name;
            Map<Object, Integer> features = combined.get(fileName);
            features.put(dataName, indices[1]);
        }

        // Write out the resulting structure
        out.add("{").indentMore();
        GeoFile[] files = map.getFiles();
        String version = new BuilderOptions().version;
        for (int k = 0; k < files.length; k++) {
            if (k > 0) out.add(",").onNewLine();
            String fileName = files[k].name;
            String source = Data.quote("http://brunelvis.org/geo/" + version + "/" + fileName + ".json");
            out.onNewLine().add(source, ":{").indentMore();
            int i = 0;
            Map<Object, Integer> features = combined.get(fileName);
            for (Map.Entry<Object, Integer> s : features.entrySet()) {
                if (i++ > 0) out.add(", ");
                if (i % 5 == 0) out.onNewLine();
                out.add("'").add(s.getKey()).add("':").add(s.getValue());
            }
            out.indentLess().onNewLine().add("}");
        }

        out.indentLess().onNewLine().add("}");
    }

    public static String fixAbbreviations(String s) {
        return s.replaceAll("st\\.[ ]*", "saint ")
                .replaceAll("dem\\.[ ]*", "democratic ")
                .replaceAll("rep\\.[ ]*", "republic ")
                .replaceAll("is\\.[ ]*", "islands ")
                .replaceAll("\u2019", "'")
                .replaceAll("&", " and ")
                .replaceAll(" [ ]+", " ").trim();
    }

    final Map<String, int[][]> featureMap;                // For each feature, a pair of [fileIndex,featureIndex]
    final GeoFile[] geoFiles;                             // Feature files we can use
    final LabelPoint[] labels;                            // Labels for the world

    private GeoAnalysis() {
        try {
            // Read in the feature information file
            InputStream is = GeoAnalysis.class.getResourceAsStream("/org/brunel/maps/geoindex.txt");
            LineNumberReader rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            geoFiles = readFileDescriptions(rdr);                       // The files
            featureMap = readFeatureDescriptions(rdr);                  // Map from features to files & ids
            rdr.close();

            // Read label file information
            is = GeoAnalysis.class.getResourceAsStream("/org/brunel/maps/locations.txt");
            rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            labels = readLabels(rdr);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        addVariantFeatureNames();

    }

    private LabelPoint[] readLabels(LineNumberReader rdr) throws IOException {
        List<LabelPoint> list = new ArrayList<LabelPoint>();
        while (true) {
            String line = rdr.readLine();
            if (line == null) break;
            list.add(LabelPoint.parse(line));
        }
        Collections.sort(list, new Comparator<LabelPoint>() {
            public int compare(LabelPoint a, LabelPoint b) {
                if (a.rank != b.rank) return a.rank - b.rank;       // lower rank goes first
                if (a.size != b.size) return b.size - a.size;       // then sort by higher size (population)
                return a.label.compareTo(b.label);                  // Just make them different ...
            }
        });
        return list.toArray(new LabelPoint[list.size()]);
    }

    private HashMap<String, int[][]> readFeatureDescriptions(LineNumberReader rdr) throws IOException {
        HashMap<String, int[][]> map = new HashMap<String, int[][]>();
        // Read the features
        while (true) {
            String line = rdr.readLine();
            if (line == null) break;                                    // End of file
            if (line.trim().length() == 0) continue;                    // Skip blank lines (should be at top)
            String[] featureLine = line.split("\\|");
            String name = featureLine[0];
            int m = featureLine.length - 1;
            int[][] data = new int[m][2];
            for (int i = 0; i < m; i++) {
                String[] s = featureLine[i + 1].split(":");
                data[i][0] = Integer.parseInt(s[0]);
                data[i][1] = Integer.parseInt(s[1]);
            }
            map.put(canonical(name), data);
        }
        return map;
    }

    private GeoFile[] readFileDescriptions(LineNumberReader rdr) throws IOException {
        // Read the names of the files and their sizes (in K)
        List<GeoFile> list = new ArrayList<GeoFile>();
        while (true) {
            String[] fileLine = rdr.readLine().split("\\|");
            if (fileLine.length != 4) break;                            // End of the file definitions
            list.add(new GeoFile(fileLine[0], fileLine[1], fileLine[2], fileLine[3]));
        }

        return list.toArray(new GeoFile[list.size()]);
    }

    // Add variants of names by normalizing removing accent marks and periods
    private void addVariantFeatureNames() {
        List<String> keys = new ArrayList<String>(featureMap.keySet());
        for (String s : keys) {
            String t = removeAccents(s);
            if (!featureMap.containsKey(t)) {
                featureMap.put(t, featureMap.get(s));
            }
            t = removePeriods(t);
            if (!featureMap.containsKey(t)) {
                featureMap.put(t, featureMap.get(s));
            }
        }
    }

    private static String canonical(String name) {
        return fixAbbreviations(name.toLowerCase());
    }

    static String removeAccents(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return PATTERN.matcher(decomposed).replaceAll("");
    }

    static String removePeriods(String s) {
        // Do not remove from XX.YY pattern
        if (s.length() == 5 && s.charAt(2) == '.') return s;
        return s.replaceAll("\\.", "");
    }

    /**
     * For a set of features, returns the mapping to use for them
     *
     * @param names feature names
     * @return resulting mapping
     */
    public GeoMapping make(Object[] names, Param[] geoParameters) {
        return GeoMapping.createGeoMapping(names, makeRequiredFiles(geoParameters), this);
    }

    public GeoMapping world() {
        return make(new Object[0], new Param[]{ Param.makeString("world")});
    }

    private List<GeoFile> makeRequiredFiles(Param[] params) {
        if (params.length == 0) return null;
        List<GeoFile> result = new ArrayList<GeoFile>();
        for (Param p : params) {
            String s = p.asString();
            if (s.equalsIgnoreCase("uk")) s = "UnitedKingdom";
            if (s.equalsIgnoreCase("usa")) s = "UnitedStatesofAmerica";
            for (GeoFile f : geoFiles) {
                if (f.name.equalsIgnoreCase(s)) {
                    result.add(f);
                    break;
                }
            }
        }
        return result;
    }

    public GeoMapping makeForPoints(PointCollection points, Param[] geoParameters) {
        return GeoMapping.createGeoMapping(points, makeRequiredFiles(geoParameters), this);
    }
}
