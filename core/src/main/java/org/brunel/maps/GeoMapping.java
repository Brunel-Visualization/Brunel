package org.brunel.maps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Defines how we will map feature names to features within files
 */
public class GeoMapping {

    public final GeoFile[] files;                                                   // The files to use
    public final Map<Object, int[]> mapping = new TreeMap<Object, int[]>();         // Feature -> [file, featureKey]
    public final List<Object> unmatched = new ArrayList<Object>();                  // Features we did not map

    /**
     * Builds itself on construction, setting all the required information fields
     *
     * @param names       names to use
     * @param geoAnalysis shared analysis information to match features against
     */
    GeoMapping(Object[] names, GeoAnalysis geoAnalysis) {
        // Search our feature files for potential maps
        // For each feature name, we have a list of information on where it is defined
        // Side effect: Records unmatched features
        Map<Integer, List<FeatureDetail>> potential = findAllMappings(names, geoAnalysis);

        // Return the best collection of files for those features.
        Set<GeoFile> fileList = chooseBestSetOfFiles(potential, geoAnalysis.geoFiles);
        files = fileList.toArray(new GeoFile[fileList.size()]);

        // Build mapping and unmatched details from the files
        buildMapping(potential);
    }

    public double[] totalBounds() {
        double[] bounds = null;
        for (GeoFile i : files) bounds = union(bounds, i.bounds);
        return bounds;
    }

    private double[] union(double[] a, double[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return new double[]{
                Math.min(a[0], b[0]), Math.max(a[1], b[1]),
                Math.min(a[2], b[2]), Math.max(a[3], b[3])
        };
    }

    private void buildMapping(Map<Integer, List<FeatureDetail>> potential) {
        for (int i = 0; i < files.length; i++) {
            List<FeatureDetail> use = potential.get(files[i].index);        // Features used in this file
            for (FeatureDetail s : use)                                     // Record match information
                mapping.put(s.name, new int[]{i, s.indexWithinFile});
        }
    }

    private Set<GeoFile> chooseBestSetOfFiles(Map<Integer, List<FeatureDetail>> potential, GeoFile[] files) {
        //Count the number of features we can match
        Set<FeatureDetail> unmatched = new HashSet<FeatureDetail>();
        for (List<FeatureDetail> f : potential.values())
            unmatched.addAll(f);

        int N = unmatched.size();

        PotentialGroup best = new PotentialGroup(N, Collections.<GeoFile>emptySet(), Collections.emptySet());
        best = searchForBestAdditions(best, best, potential, files);
        return best.files;
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
    private PotentialGroup searchForBestAdditions(PotentialGroup bestSoFar, PotentialGroup current, Map<Integer, List<FeatureDetail>> potential, GeoFile[] files) {
        if (current == null) return bestSoFar;                          // Cannot do anything better
        if (current.isBetter(bestSoFar)) bestSoFar = current;           // If we are the best, update
        if (current.complete()) return bestSoFar;                       // If we are complete, cannot add anything

        Map<Integer, List<FeatureDetail>> tryThese = new LinkedHashMap<Integer, List<FeatureDetail>>(potential);

        while (!tryThese.isEmpty()) {
            int k = tryThese.keySet().iterator().next();
            List<FeatureDetail> features = tryThese.remove(k);
            PotentialGroup g = current.add(files[k], features);
            PotentialGroup bestForG = searchForBestAdditions(bestSoFar, g, tryThese, files);
            if (bestForG.isBetter(bestSoFar))
                bestSoFar = bestForG;
        }

        return bestSoFar;

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
