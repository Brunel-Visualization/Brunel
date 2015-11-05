package org.brunel.maps;

import java.util.ArrayList;
import java.util.Arrays;
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

    private final GeoFile[] targetFiles;                                            // The files that match features we care about
    private final List<FeatureDetail>[] targetFeatures;                             // The features contained in each target file
    public final Map<Object, int[]> mapping = new TreeMap<Object, int[]>();         // Feature -> [file, featureKey]
    public final List<Object> unmatched = new ArrayList<Object>();                  // Features we did not map

    private GeoFileGroup best;                                                    // We search to determine this
    public final GeoFile[] result;                                                  // The files to use

    /**
     * Builds itself on construction, setting all the required information fields
     *
     * @param names       names to use
     * @param geoAnalysis shared analysis information to match features against
     */
    @SuppressWarnings("unchecked")
    GeoMapping(Object[] names, GeoAnalysis geoAnalysis) {
        // Search our feature files for potential maps
        Map<Integer, List<FeatureDetail>> potential = findAllMappings(names, geoAnalysis);
        targetFiles = new GeoFile[potential.size()];
        targetFeatures = new List[potential.size()];
        int index = 0;
        for (Map.Entry<Integer, List<FeatureDetail>> e : potential.entrySet()) {
            targetFiles[index] = geoAnalysis.geoFiles[e.getKey()];
            targetFeatures[index] = e.getValue();
            index++;
        }

        // Calculate the best collection of files for those features.
        searchForBestSubset();
        result = best.files.toArray(new GeoFile[best.files.size()]);
        Arrays.sort(result);

        // Build mapping
        for (int i = 0; i < result.length; i++) {
            List<FeatureDetail> use = potential.get(result[i].index);        // Features used in this file
            for (FeatureDetail s : use)                                     // Record match information
                mapping.put(s.name, new int[]{i, s.indexWithinFile});
        }
    }

    public GeoMapping(double[] bounds, GeoAnalysis geoAnalysis) {
        // Really simple -- just find the one file that fits best
        GeoFile best = null;
        for (GeoFile f : geoAnalysis.geoFiles) {
            if (best == null) best = f;
            else if (best.fitExcess(bounds) > f.fitExcess(bounds)) best = f;
        }
        targetFiles = new GeoFile[] { best};
        targetFeatures = new List[0];
        result = targetFiles;
    }

    public int fileCount() {
        return result.length;
    }

    public double[] totalBounds() {
        double[] bounds = null;
        for (GeoFile i : result) bounds = union(bounds, i.bounds);
        return bounds;
    }

    public static double[] union(double[] a, double[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return new double[]{
                Math.min(a[0], b[0]), Math.max(a[1], b[1]),
                Math.min(a[2], b[2]), Math.max(a[3], b[3])
        };
    }

    private void searchForBestSubset() {
        //Count the number of features we can match
        Set<FeatureDetail> unmatched = new HashSet<FeatureDetail>();
        for (List<FeatureDetail> f : targetFeatures)
            unmatched.addAll(f);

        int N = unmatched.size();
        best = new GeoFileGroup(N, Collections.<GeoFile>emptySet(), Collections.emptySet());

        // Create list of possible ones to use, sorted with the most features first

        List<Integer> possibles = new ArrayList<Integer>();
        for (int i = 0; i < targetFiles.length; i++) possibles.add(i);
        Collections.sort(possibles, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return targetFeatures[b].size() - targetFeatures[a].size();
            }
        });
        searchForBestAdditions(best, possibles);
    }

    private Map<Integer, List<FeatureDetail>> findAllMappings(Object[] names, GeoAnalysis geoAnalysis) {
        Map<Integer, List<FeatureDetail>> contained = new HashMap<Integer, List<FeatureDetail>>();
        for (Object s : names) {
            int[][] item = findFeature(s, geoAnalysis.featureMap);
            if (item == null) {
                unmatched.add(s);
            } else {
                for (int[] i : item) {
                    Integer file = i[0];
                    List<FeatureDetail> content = contained.get(file);
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
    private int[][] findFeature(Object key, Map<String, int[][]> featureMap) {
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
    private void searchForBestAdditions(GeoFileGroup current, List<Integer> possibles) {
        if (current.isBetter(best)) best = current;                     // If we are the best, update
        if (possibles.isEmpty()) return;                                // No more we can do
        int maxImprovement = targetFeatures[possibles.get(0)].size();   // Additions can at best add this many
        if (current.cannotImprove(best, maxImprovement)) return;        // If we cannot get better, be done
        LinkedList<Integer> working = new LinkedList<Integer>(possibles);

        while (!working.isEmpty()) {
            int k = working.removeFirst();
            GeoFileGroup trial = current.add(targetFiles[k], targetFeatures[k]);
            if (trial != null) searchForBestAdditions(trial, working);
        }
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
