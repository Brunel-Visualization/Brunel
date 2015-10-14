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

    public final String[] files;                                                    // The files to use
    public final Map<Object, int[]> mapping = new TreeMap<Object, int[]>();         // Feature -> [file, featureKey]
    public final List<Object> unmatched = new ArrayList<Object>();                  // Features we did not map

    /**
     * Builds itself on construction, setting all the required information fields
     * @param names names to use
     * @param geoAnalysis shared analysis information to match features against
     */
     GeoMapping(Object[] names, GeoAnalysis geoAnalysis) {
        // Search our feature files for potential maps
        // For each feature name, we have a list of information on where it is defined
        // Side effect: Records unmatched features
        Map<Integer, List<FeatureDetail>> potential = findAllMappings(names, geoAnalysis);

        // Return the best collection of files for those features.
        // Side effect: sets the mappings
        List<String> fileList = makeBestMatches(geoAnalysis, potential);

        files = fileList.toArray(new String[fileList.size()]);
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

    private Integer findBest(Map<Integer, List<FeatureDetail>> contained, int[] sizes) {
        // The best item has the most items in it. In case of tie, use smallest file
        Integer best = null;
        int bestContentCount = 0;
        for (Map.Entry<Integer, List<FeatureDetail>> e : contained.entrySet()) {
            Integer k = e.getKey();
            List<FeatureDetail> v = e.getValue();
            if (v.size() > bestContentCount) {
                // Wins by having more items
                best = k;
                bestContentCount = v.size();
            } else if (v.size() == bestContentCount && sizes[k] < sizes[best]) {
                // Wins by having same number of items, but smaller size
                best = k;
                bestContentCount = v.size();
            }
        }
        return best;
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

    private List<String> makeBestMatches(GeoAnalysis geoAnalysis, Map<Integer, List<FeatureDetail>> potential) {
        List<String> fileList = new ArrayList<String>();

        // We find the best file, and remove any potential matches for it as they are now actually matched
        // Keep doing this until there are no potential matches left
        while (!potential.isEmpty()) {
            Integer sourceIndex = findBest(potential, geoAnalysis.sizes);   // Best index among source files
            int resultIndex = fileList.size();                              // The index into this output file
            fileList.add(geoAnalysis.geoFiles[sourceIndex]);                // We will use this file
            List<FeatureDetail> features = potential.remove(sourceIndex);   // We will use these details (now actual)
            removeItems(potential, features);                               // remove any matches from other lists
            for (FeatureDetail s : features)                                // Record match information
                mapping.put(s.name, new int[]{resultIndex, s.indexWithinFile});
        }
        return fileList;
    }

    private void removeItems(Map<Integer, List<FeatureDetail>> contained, List<FeatureDetail> removeThese) {
        Set<Integer> keys = new HashSet<Integer>(contained.keySet());
        for (Integer k : keys) {
            List<FeatureDetail> list = contained.get(k);
            list.removeAll(removeThese);
            if (list.isEmpty()) contained.remove(k);
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
