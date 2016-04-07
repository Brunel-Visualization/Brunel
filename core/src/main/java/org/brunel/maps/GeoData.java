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
import java.util.Comparator;
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

    MappedLists<GeoFile, Object> mapFeaturesToFiles(Object[] names, Collection<Object> unmatched) {
        MappedLists<GeoFile, Object> contained = new MappedLists<>();
        for (Object s : names) {
            String key = s.toString();
            int[][] item = featureByName(key);
            if (item == null) {
                unmatched.add(s);
            } else {
                for (int[] i : item) {
                    GeoFile file = geoFiles[i[0]];
                    contained.add(file, new IndexedFeature(key, i[1]));
                }
            }
        }
        return contained;
    }

    private final Map<String, int[][]> featureMap;        // For each feature, a pair of [fileIndex,featureIndex]
    private final Map<String, GeoFile> filesByName;       // A map of canonical name to file
    private final Map<String, LabelPoint> labelsByName;   // A map of canonical name to labels
    private final GeoFile[] geoFiles;                     // Feature files we can use
    private final LabelPoint[] labels;                    // Labels for the world

    private GeoData() {
        try {
            // Read in the feature information file
            InputStream is = GeoData.class.getResourceAsStream("/org/brunel/maps/geoindex.txt");
            LineNumberReader rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            geoFiles = readFileDescriptions(rdr);                       // The files
            featureMap = readFeatureDescriptions(rdr);                  // Map from features to files & ids
            rdr.close();

            filesByName = makeFileNameMap(geoFiles);                    // So we can identify them by name

            // Read label file information
            is = GeoData.class.getResourceAsStream("/org/brunel/maps/locations.txt");
            rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));
            labels = readLabels(rdr);
            rdr.close();

            labelsByName = makeLabelsMap();                             // Create a map form names to labels
            placeLabelsInFiles();                                       // Add labels to geo files

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        addVariantFeatureNames();

    }

    private HashMap<String, LabelPoint> makeLabelsMap() {
        HashMap<String, LabelPoint> map = new HashMap<>();
        for (LabelPoint s : labels) {
            String name = GeoNaming.canonical(s.label);
            LabelPoint previous = map.put(name, s);
            // There are multiple places with the same name (e.g. Paris, Texas vs Paris, France)
            // So if the one that was in there is more important than this one, restore the original version
            if (previous != null && previous.compareTo(s) > 0) {
                map.put(name, previous);
            }
        }
        return map;
    }

    public int[][] featureByName(String s) {
        s = GeoNaming.canonical(s);
        int[][] result = featureMap.get(s);
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

    private void placeLabelsInFiles() {
        for (LabelPoint p : labels) {
            int[][] where = featureMap.get(GeoNaming.canonical(p.parent0));
            if (where != null) {
                for (int[] item : where) {
                    geoFiles[item[0]].pts.add(p);
                }
            }
            where = featureMap.get(GeoNaming.canonical(p.parent1));
            if (where != null) {
                for (int[] item : where) {
                    geoFiles[item[0]].pts.add(p);
                }
            }
        }
    }

    private LabelPoint[] readLabels(LineNumberReader rdr) throws IOException {
        List<LabelPoint> list = new ArrayList<>();
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
        HashMap<String, int[][]> map = new HashMap<>();
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
            map.put(GeoNaming.canonical(name), data);
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
        return GeoMapping.createGeoMapping(names, makeRequiredFiles(geoParameters), this);
    }

    GeoMapping world() {
        return make(new Object[0], new Param[]{Param.makeString("world")});
    }

    private List<GeoFile> makeRequiredFiles(Param[] params) {
        if (params.length == 0) return null;
        Set<GeoFile> byFeature = new HashSet<>();
        List<GeoFile> result = new ArrayList<>();
        for (Param p : params) {
            String key = GeoNaming.canonical(p.asString());
            GeoFile f = filesByName.get(key);
            if (f != null) {
                result.add(f);
            } else {
                int[][] feature = featureMap.get(key);
                if (feature != null) {
                    byFeature.add(geoFiles[feature[0][0]]);
                } else {
                    LabelPoint location = labelsByName.get(key);
                    if (location != null)
                        byFeature.add(smallestFileContaining(location));

                }
            }

        }
        result.addAll(byFeature);
        return result;
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
        return GeoMapping.createGeoMapping(hull, makeRequiredFiles(geoParameters), this);
    }
}
