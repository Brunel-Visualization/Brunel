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

    public static GeoMapping createGeoMapping(PointCollection points, List<GeoFile> required, GeoAnalysis geoAnalysis) {
        HashSet<Object> unmatched = new HashSet<Object>();
        Map<GeoFile, List> map = mapBoundsToFiles(points, geoAnalysis);
        return new GeoMapping(required, unmatched, map);
    }

    static GeoMapping createGeoMapping(Object[] names, List<GeoFile> required, GeoAnalysis geoAnalysis) {
        HashSet<Object> unmatched = new HashSet<Object>();
        Map<GeoFile, List> map = mapFeaturesToFiles(names, geoAnalysis, unmatched);
        GeoMapping mapping = new GeoMapping(required, unmatched, map);
        mapping.buildFeatureMap();
        return mapping;
    }

    private final Map<Object, int[]> featureMap = new TreeMap<Object, int[]>();     // Feature -> [file, featureKey]
    private final Set<Object> unmatched;                                            // Features we did not map
    private final GeoFile[] result;                                                 // The files to use
    private GeoFileGroup best;                                                      // We search to determine this
    private final Map<GeoFile, List> potential;                                     // Files that contain wanted items

    GeoMapping(List<GeoFile> required, Set<Object> unmatched, Map<GeoFile, List> potential) {
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

    void buildFeatureMap() {
        // Build featureMap -- only needed for features, not points
        for (int i = 0; i < result.length; i++) {
            List<FeatureDetail> use = potential.get(result[i]);        // Features used in this file
            for (FeatureDetail s : use)                                // Record match information
                featureMap.put(s.name, new int[]{i, s.indexWithinFile});
        }
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

    // Remove any not mentioned by the user's required list
    private Map<GeoFile, List> filter(Map<GeoFile, List> desired, List<GeoFile> required) {
        if (required == null || required.isEmpty()) return desired;

        Map<GeoFile, List> filtered = new HashMap<GeoFile, List>();
        for (Map.Entry<GeoFile, List> e : desired.entrySet())
            if (required.contains(e.getKey())) filtered.put(e.getKey(), e.getValue());
        return filtered;
    }

    // Create a map from Geofile index to the points that file contains.
    private static Map<GeoFile, List> mapBoundsToFiles(PointCollection points, GeoAnalysis geoAnalysis) {
        HashMap<GeoFile, List> map = new HashMap<GeoFile, List>();
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

    public int fileCount() {
        return result.length;
    }

    public Rect totalBounds() {
        Rect bounds = null;
        for (GeoFile i : result) bounds = i.bounds.union(bounds);
        return bounds;
    }

    private static Map<GeoFile, List> mapFeaturesToFiles(Object[] names, GeoAnalysis geoAnalysis, Collection<Object> unmatched) {
        Map<GeoFile, List> contained = new HashMap<GeoFile, List>();
        for (Object s : names) {
            int[][] item = findFeature(s, geoAnalysis.featureMap);
            if (item == null) {
                unmatched.add(s);
            } else {
                for (int[] i : item) {
                    GeoFile file = geoAnalysis.geoFiles[i[0]];
                    List content = contained.get(file);
                    if (content == null) {
                        content = new ArrayList<FeatureDetail>();
                        contained.put(file, content);
                    }
                    content.add(new FeatureDetail(s, i[0], i[1]));
                }
            }
        }
        return contained;
    }

    // Find a match, if necessary by removing accent marks and periods
    private static int[][] findFeature(Object key, Map<String, int[][]> featureMap) {
        String s = key.toString().toLowerCase();
        int[][] result = featureMap.get(s);
        if (result != null) return result;
        s = GeoAnalysis.removeAccents(s);
        result = featureMap.get(s);
        if (result != null) return result;
        s = GeoAnalysis.removePeriods(s);
        return featureMap.get(s);
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
        final int featureFileIndex;
        final int indexWithinFile;

        private FeatureDetail(Object name, int featureFileIndex, int indexWithinFile) {
            this.name = name;
            this.featureFileIndex = featureFileIndex;
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
