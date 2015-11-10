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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A set of files that could potentially cover the mapping we need
 */
class GeoFileGroup {
    private static final int MAX_FILES = 3;                     // No more files than this allowed in a group

    /**
     * Make an empty group that wants to contain a defined number of items
     *
     * @param containedItems Map form files to those items we want groups to contain
     * @return empty group
     */
    public static GeoFileGroup makeEmpty(Map<GeoFile, List<Object>> containedItems) {
        // Count the number of features we can match
        Set<Object> all = new HashSet<Object>();
        for (List<Object> f : containedItems.values()) all.addAll(f);
        return new GeoFileGroup(all.size(), containedItems, Collections.<GeoFile>emptySet(), Collections.emptySet());
    }
    public final Set<GeoFile> files;                            // These are the files
    private final Map<GeoFile, List<Object>> itemsMap;          // Map form files to contained items
    private final int requiredContentCount;                     // We want to match this many features
    private final Set<Object> content;                          // These are items we do contain
    private Rect totalBounds;                                   // bounds for all the group

    private GeoFileGroup(int requiredCount, Map<GeoFile, List<Object>> itemsMap, Collection<GeoFile> files, Collection<?> features) {
        this.requiredContentCount = requiredCount;
        this.itemsMap = itemsMap;
        this.files = new LinkedHashSet<GeoFile>(files);
        this.content = new HashSet<Object>(features);
    }

    /**
     * Attempts to add the required file to the current set of files
     *
     * @param file file to add
     * @return null if adding was no help, otherwise a new group
     */
    public GeoFileGroup add(GeoFile file) {
        if (files.contains(file)) return null;                              // Already included so no need to add
        Set<Object> combinedFeatures = new HashSet<Object>(content);
        if (!combinedFeatures.addAll(itemsMap.get(file))) return null;      // Failed to add features
        Set<GeoFile> combinedFiles = new HashSet<GeoFile>(files);
        combinedFiles.add(file);
        return new GeoFileGroup(requiredContentCount, itemsMap, combinedFiles, combinedFeatures);
    }

    public boolean cannotImprove(GeoFileGroup best, int maxFeaturesPerFile) {
        if (content.size() == requiredContentCount) return true;                // Nothing new can get added
        if (files.size() == MAX_FILES) return true;                             // Limited number of files

        // An upper bound on the number of features we could add
        int upperFeatureBound = content.size() + (MAX_FILES - files.size()) * maxFeaturesPerFile;
        return upperFeatureBound < best.content.size();
    }

    public boolean isBetter(GeoFileGroup o) {
        if (o == this) return false;

        // More features are better
        int d = content.size() - o.content.size();
        if (d < 0) return false;
        if (d > 0) return true;

        double myScore = area() * (1 + files.size());
        double otherScore = o.area() * (1 + o.files.size());

        return myScore < otherScore;
    }

    private double area() {
        if (files.isEmpty()) return 0;
        if (totalBounds == null)
            for (GeoFile f : files) totalBounds = f.bounds.union(totalBounds);
        return totalBounds.area();
    }

    public String toString() {
        return files + ":" + content.size() + "/" + requiredContentCount;
    }
}
