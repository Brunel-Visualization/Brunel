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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Defines how we will map feature names to features within files
 */
public class GeoMapping {

    private static final String MAPPING = "britain:united kingdom|great britain:united kingdom|united states:united states of america|usa:united states of america" +
            "|burma:myanmar|vatican city:vatican|holy see:vatican";
    private static final Map<String, String> commonNames = new HashMap<String, String>();

    static {
        for (String s: MAPPING.split("\\|")) {
            String[] p = s.split(":");
            commonNames.put(p[0], p[1]);
        }
    }

    public static GeoMapping createGeoMapping(PointCollection points, List<GeoFile> required, GeoAnalysis geoAnalysis) {
        HashSet<Object> unmatched = new HashSet<Object>();
        Map<GeoFile, List<Object>> map = mapBoundsToFiles(points, geoAnalysis);
        return new GeoMapping(required, unmatched, map);
    }

    // Create a map from Geofile index to the points that file contains.
    private static Map<GeoFile, List<Object>> mapBoundsToFiles(PointCollection points, GeoAnalysis geoAnalysis) {
        HashMap<GeoFile, List<Object>> map = new HashMap<GeoFile, List<Object>>();
        if (points.isEmpty()) return map;
        Rect bounds = points.bounds();
        GeoFile[] geoFiles = geoAnalysis.geoFiles;
        for (GeoFile f : geoFiles) {
            if (!bounds.intersects(f.bounds)) continue;             // Ignore if outside the bounds
            List content = new ArrayList();
            for (Point p : points.convexHull())
                if (f.covers(p)) content.add(p);
            if (!content.isEmpty()) map.put(f, content);
        }
        return map;
    }

    static GeoMapping createGeoMapping(Object[] names, List<GeoFile> required, GeoAnalysis geoAnalysis) {
        HashSet<Object> unmatched = new HashSet<Object>();
        Map<GeoFile, List<Object>> map = mapFeaturesToFiles(names, geoAnalysis, unmatched);
        GeoMapping mapping = new GeoMapping(required, unmatched, map);
        mapping.buildFeatureMap();
        return mapping;
    }

    private static Map<GeoFile, List<Object>> mapFeaturesToFiles(Object[] names, GeoAnalysis geoAnalysis, Collection<Object> unmatched) {
        Map<GeoFile, List<Object>> contained = new HashMap<GeoFile, List<Object>>();
        for (Object s : names) {
            int[][] item = findFeature(s, geoAnalysis.featureMap);
            if (item == null) {
                unmatched.add(s);
            } else {
                for (int[] i : item) {
                    GeoFile file = geoAnalysis.geoFiles[i[0]];
                    List<Object> content = contained.get(file);
                    if (content == null) {
                        content = new ArrayList<Object>();
                        contained.put(file, content);
                    }
                    content.add(new FeatureDetail(s, i[1]));
                }
            }
        }
        return contained;
    }

    private void buildFeatureMap() {
        // Build featureMap -- only needed for features, not points
        for (int i = 0; i < result.length; i++) {
            List<Object> use = potential.get(result[i]);        // Features used in this file
            for (Object o : use) {                              // Record match information
                FeatureDetail s = (FeatureDetail) o;
                featureMap.put(s.name, new int[]{i, s.indexWithinFile});
            }
        }
    }

    // Find a match, if necessary by removing accent marks and periods
    private static int[][] findFeature(Object key, Map<String, int[][]> featureMap) {
        String s = key.toString().toLowerCase().trim();
        int[][] name = findName(featureMap, s);
        if (name != null) return name;

        int p = s.indexOf(',');
        if (p > 0) {
            // Instead of  "Netherlands, The", try "The Netherlands"
            name = findName(featureMap, s.substring(p+1).trim() + " " + s.substring(0, p));
            if (name != null) return name;
            // Instead of  "Netherlands, The", try "Netherlands"
            name = findName(featureMap, s.substring(0, p));
            if (name != null) return name;
        }

        p = s.indexOf('(');
        if (p > 0) {
            // Things like "Myanmar(burma)"
            name = findName(featureMap, s.substring(0, p).trim());
            if (name != null) return name;
        }

        // Replace common abbreviations
        String t = GeoAnalysis.fixAbbreviations(s);

        if (!t.equals(s)) {
            name = findName(featureMap, t);
            if (name != null) return name;
        }

        return null;
    }

    private static int[][] findName(Map<String, int[][]> featureMap, String s) {
        s = s.replaceAll("  ", " ");
        String common = commonNames.get(s);
        if (common != null) s = common;

        // Lots of variations
        if (s.startsWith("united kingdom of")) s = "united kingdom";

        // Try the name, then try removing accents and special characters
        int[][] result = featureMap.get(s);
        if (result != null) return result;
        s = GeoAnalysis.removeAccents(s);
        result = featureMap.get(s);
        if (result != null) return result;
        s = GeoAnalysis.removePeriods(s);
        return featureMap.get(s);
    }

    private final Map<Object, int[]> featureMap = new TreeMap<Object, int[]>();     // Feature -> [file, featureKey]
    private final Set<Object> unmatched;                                            // Features we did not map
    private final GeoFile[] result;                                                 // The files to use
    private final Map<GeoFile, List<Object>> potential;                             // Files that contain wanted items
    private GeoFileGroup best;                                                      // We search to determine this

    private GeoMapping(List<GeoFile> required, Set<Object> unmatched, Map<GeoFile, List<Object>> potential) {
        this.potential = filter(potential, required);   // If required is defined, filter to only show those files
        this.unmatched = unmatched;                     // Unmatched items
        searchForBestSubset();                          // Calculate the best collection of files for those features.

        // Define the desired files to use
        if (required != null) {
            result = required.toArray(new GeoFile[required.size()]);
        } else {
            result = best.files.toArray(new GeoFile[best.files.size()]);
            Arrays.sort(result);
        }
    }

    public int fileCount() {
        return result.length;
    }

    public Map<Object, int[]> getFeatureMap() {
        return featureMap;
    }

    public GeoFile[] getFiles() {
        return result;
    }

    public Set<Object> getUnmatched() {
        return unmatched;
    }

    public Rect totalBounds() {
        Rect bounds = null;
        for (GeoFile i : result) bounds = i.bounds.union(bounds);
        return bounds;
    }

    // Remove any not mentioned by the user's required list
    private Map<GeoFile, List<Object>> filter(Map<GeoFile, List<Object>> desired, List<GeoFile> required) {
        if (required == null || required.isEmpty()) return desired;

        Map<GeoFile, List<Object>> filtered = new HashMap<GeoFile, List<Object>>();
        for (Map.Entry<GeoFile, List<Object>> e : desired.entrySet())
            if (required.contains(e.getKey())) filtered.put(e.getKey(), e.getValue());
        return filtered;
    }

    // Recursively search for the best files to add
    private void searchForBestAdditions(GeoFileGroup current, List<GeoFile> possibles) {
        // System.out.println("Searching w/ current=" + current + ", base=" +  best+ ", possibles=" + possibles);
        if (current.isBetter(best)) {
            // System.out.println(" *** improved");
            best = current;                     // If we are the best, update
        }
        if (possibles.isEmpty()) return;                                    // No more we can do
        int maxImprovement = potential.get(possibles.get(0)).size();        // Additions can at best add this many
        if (current.cannotImprove(best, maxImprovement)) return;            // If we cannot get better, stop searching

        // recurse to search for best combination
        LinkedList<GeoFile> working = new LinkedList<GeoFile>(possibles);
        while (!working.isEmpty()) {
            GeoFile k = working.removeFirst();
            GeoFileGroup trial = current.add(k);                            // Will be null if known to be useless
            if (trial != null) searchForBestAdditions(trial, working);
        }
    }

    private void searchForBestSubset() {
        best = GeoFileGroup.makeEmpty(potential);

        // Create list of possible ones to use, sorted with the most features first
        List<GeoFile> possibles = new ArrayList<GeoFile>(potential.keySet());
        Collections.sort(possibles, new Comparator<GeoFile>() {
            public int compare(GeoFile a, GeoFile b) {
                return potential.get(b).size() - potential.get(a).size();
            }
        });
        searchForBestAdditions(best, possibles);
    }

    private static class FeatureDetail {
        final Object name;
        final int indexWithinFile;

        private FeatureDetail(Object name, int indexWithinFile) {
            this.name = name;
            this.indexWithinFile = indexWithinFile;
        }

        public int hashCode() {
            return name.hashCode();
        }

        // Unsafe for speed because we use it in a very limited domain
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object o) {
            return name.equals(((FeatureDetail) o).name);
        }
    }

}
