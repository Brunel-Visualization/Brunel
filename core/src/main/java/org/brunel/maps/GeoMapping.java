package org.brunel.maps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Defines how we will map feature names to features within files
 */
public class GeoMapping {

    public final Map<Object, int[]> mapping = new TreeMap<Object, int[]>();         // Feature -> [file, featureKey]
    public final List<Object> unmatched = new ArrayList<Object>();                  // Features we did not map
    public final GeoFile[] result;                                                  // The files to use
    private final GeoFile[] targetFiles;                                            // The files that match features we care about
    private final List<FeatureDetail>[] targetFeatures;                             // The features contained in each target file
    private GeoFileGroup best;                                                    // We search to determine this

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

    public GeoMapping(Rect bounds, GeoAnalysis geoAnalysis) {
        // Create a list of all GeoFiles that intersect the required area
        // Keep the whole world one separate
        GeoFile world = null;

        List<GeoFile> base = new ArrayList<GeoFile>();
        for (GeoFile f : geoAnalysis.geoFiles)
            if (f.name.equalsIgnoreCase("world")) world = f;
            else if (f.bounds.intersects(bounds)) base.add(f);

        // Create a grid of nine points covering the target area. We want all of them to be included
        Set<double[]> pointsToInclude = new HashSet<double[]>();

        // Shrink the bounds a little

        Collections.addAll(pointsToInclude, bounds.makeBoundaryPoints());
        pointsToInclude.add(new double[]{bounds.cx(), bounds.cy()});

        // Find which points are covered
        final Map<GeoFile, List<double[]>> contained = new HashMap<GeoFile, List<double[]>>();
        for (GeoFile f : base) {
            List<double[]> good = new ArrayList<double[]>();
            for (double[] p : pointsToInclude) if (f.covers(p[0], p[1])) good.add(p);
            contained.put(f, good);
        }

        // Sort the most likely useful first
        Collections.sort(base, new Comparator<GeoFile>() {
            public int compare(GeoFile a, GeoFile b) {
                double scoreA = Math.pow(contained.get(a).size(), 2) / Math.pow(a.bounds.area(), 1);
                double scoreB = Math.pow(contained.get(b).size(), 2) / Math.pow(b.bounds.area(), 1);
                return Double.compare(scoreB, scoreA);
            }
        });

        // In order add if they cover a new point
        List<GeoFile> best = new ArrayList<GeoFile>();
        for (GeoFile f : base) {
            boolean worthwhile = false;
            for (Iterator<double[]> it = pointsToInclude.iterator(); it.hasNext(); ) {
                double[] p = it.next();
                if (f.covers(p[0], p[1])) {
                    worthwhile = true;
                    it.remove();
                }
            }
            if (worthwhile) best.add(f);
            if (best.size() == 3) break;            // No more than three files
        }

        // Sort so the largest area files are first (will be on the bottom)
        Collections.sort(best, new Comparator<GeoFile>() {
            public int compare(GeoFile o1, GeoFile o2) {
                return Double.compare(o2.bounds.area(), o1.bounds.area());
            }
        });

        if (pointsToInclude.size() > 3) {
            // Missing 4+ points -- use the whole world
            targetFiles = new GeoFile[]{world};
        } else {
            // The smaller set fits well
            targetFiles = best.toArray(new GeoFile[best.size()]);
        }
        targetFeatures = new List[0];
        result = targetFiles;
    }

    private double intersectionSize(Rect a, Rect b) {
        Rect r = a.intersection(b);
        return r == null ? -1 : r.area();
    }

    public int fileCount() {
        return result.length;
    }

    public Rect totalBounds() {
        Rect bounds = null;
        for (GeoFile i : result) bounds = i.bounds.union(bounds);
        return bounds;
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
