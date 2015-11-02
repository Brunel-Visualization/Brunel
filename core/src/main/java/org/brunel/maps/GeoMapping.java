package org.brunel.maps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        List<GeoFile> fileList = chooseFiles(potential, geoAnalysis.geoFiles);
        files = fileList.toArray(new GeoFile[fileList.size()]);

        // Build mapping and unmatched details from the files
        buildMapping(potential);
    }

    private void buildMapping(Map<Integer, List<FeatureDetail>> potential) {
        for (int i = 0; i < files.length; i++) {
            List<FeatureDetail> use = potential.get(files[i].index);        // Features used in this file
            for (FeatureDetail s : use)                                     // Record match information
                mapping.put(s.name, new int[]{i, s.indexWithinFile});
        }
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


    private List<GeoFile> chooseFiles(Map<Integer, List<FeatureDetail>> potential, GeoFile[] files) {
        List<GeoFile> fileList = new ArrayList<GeoFile>();          // The currently chosen files
        double[] bounds = null;                                     // The bounds of those files

        // Copy the potential list to those that we have not yet matched (initially, all of them)
        Map<Integer, List<FeatureDetail>> unfound = new HashMap<Integer, List<FeatureDetail>>(potential);

        // We find the best file, and remove any potential matches for it as they are now actually matched
        // Keep doing this until there are no potential matches left
        while (!unfound.isEmpty()) {
            Integer idx = findBest(unfound, files, bounds);       // Best index among source files
            bounds = union(bounds, files[idx].bounds);              // Update total bounds
            fileList.add(files[idx]);                               // We will use this file
            List<FeatureDetail> features = unfound.remove(idx);   // We will use these details (now actual)
            removeItems(unfound, features);                       // Remove any matches from other lists
        }
        return fileList;
    }

    private double[] union(double[] a, double[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return new double[]{
                Math.min(a[0], b[0]), Math.max(a[1], b[1]),
                Math.min(a[2], b[2]), Math.max(a[3], b[3])
        };
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

    private Integer findBest(Map<Integer, List<FeatureDetail>> contained, GeoFile[] files, double[] bounds) {
        // The best score is a ratio of the "goodness" (number of features) divided by the "badness" -- increase in bounds size

        Integer best = null;
        double bestScore = 0;
        for (Map.Entry<Integer, List<FeatureDetail>> e : contained.entrySet()) {
            Integer k = e.getKey();
            List<FeatureDetail> v = e.getValue();

            double good = v.size();                                     // Features we want
            double[] increasedBounds = union(bounds, files[k].bounds);
            double sizeDelta = Math.pow(area(increasedBounds) - area(bounds), 0.4)+ 0.001;

            double d = Math.pow(good, 2) / sizeDelta;
            if (d > bestScore) {
                best = k;
                bestScore = d;
            }
        }
        return best;
    }

    private double area(double[] bounds) {
        return bounds == null ? 0 : (bounds[1] - bounds[0]) * (bounds[3] - bounds[2]);
    }

    private void removeItems(Map<Integer, List<FeatureDetail>> contained, List<FeatureDetail> removeThese) {
        Set<Integer> keys = new HashSet<Integer>(contained.keySet());
        for (Integer k : keys) {
            List<FeatureDetail> list = contained.get(k);
            list.removeAll(removeThese);
            if (list.isEmpty()) contained.remove(k);
        }
    }

    public double[] totalBounds() {
        double[] bounds = null;
        for (GeoFile i : files) bounds = union(bounds, i.bounds);
        return bounds;
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
