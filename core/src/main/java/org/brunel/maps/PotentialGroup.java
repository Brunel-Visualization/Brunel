package org.brunel.maps;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A set of files that could potentially cover the mapping we need
 */
public class PotentialGroup {
    public final Set<GeoFile> files;
    private final int requiredFeatureCount;
    private final Set<Object> featureSet;
    private Double area;

    public PotentialGroup(int requiredFeatureCount, Collection<GeoFile> files, Collection<?> features) {
        this.requiredFeatureCount = requiredFeatureCount;
        this.files = new LinkedHashSet<GeoFile>(files);
        this.featureSet = new HashSet<Object>(features);
    }

    public PotentialGroup add(GeoFile file, List<?> features) {
        if (files.contains(file)) return null;                      // Already included
        Set<Object> combinedFeatures = new HashSet<Object>(featureSet);
        if (!combinedFeatures.addAll(features)) return null;        // if not change, don't use this
        Set<GeoFile> combinedFiles = new HashSet<GeoFile>(files);
        combinedFiles.add(file);
        return new PotentialGroup(requiredFeatureCount, combinedFiles, combinedFeatures);
    }

    public boolean complete() {
        return featureSet.size() == requiredFeatureCount;
    }

    public boolean isBetter(PotentialGroup o) {
        if (o == this) return false;

        // More features are better
        int d = featureSet.size() - o.featureSet.size();
        if (d < 0) return false;
        if (d > 0) return true;

        double myScore = area() * (1 + files.size());
        double otherScore = o.area() * (1 + o.files.size());

        return myScore < otherScore;
    }

    private double area() {
        if (files.isEmpty()) return 0;
        if (area == null) {
            double[] bounds = null;
            for (GeoFile f : files) bounds = union(bounds, f.bounds);
            area = (bounds[1] - bounds[0]) * (bounds[3] - bounds[2]);
        }
        return area;
    }

    private double[] union(double[] a, double[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return new double[]{
                Math.min(a[0], b[0]), Math.max(a[1], b[1]),
                Math.min(a[2], b[2]), Math.max(a[3], b[3])
        };
    }

}
