/*
 * Copyright (c) 2016 IBM Corporation and others.
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

package org.brunel.data.util;

import org.brunel.data.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Associates items with integers
 */
public class MapInt {
    private final Map<Object, Integer> map = new HashMap<>();
    private int totalCount;
    private int maxCount;

    public int get(Object o) {
        Integer v = map.get(o);
        return v == null ? 0 : v;
    }

    public int[] getCounts(Object[] vals) {
        int[] result = new int[vals.length];
        for (int i = 0; i < result.length; i++) result[i] = get(vals[i]);
        return result;
    }

    /**
     * Returns all keys in the order they have been indexed
     *
     * @return array of keys
     */
    public Object[] getIndexedKeys() {
        Object[] results = new Object[size()];
        for (Object o : map.keySet()) results[map.get(o)] = o;
        return results;
    }

    public void increment(Object o) {
        if (o != null) {
            int v = get(o) + 1;
            map.put(o, v);
            totalCount++;
            maxCount = Math.max(maxCount, v);
        }
    }

    public int getTotalCount() {
        return totalCount;
    }

    public Object mode() {
        if (isEmpty()) return null;
        List<Object> list = new ArrayList<>();
        for (Object s : map.keySet())
            if (map.get(s) == maxCount) list.add(s);

        // Set the mode to be the middle of the sorted list of modes
        Object[] array = list.toArray(new Object[list.size()]);
        Data.sort(array);
        return array[(array.length - 1) / 2];
    }

    /**
     * If not present in the map, add each with an index number equal to the map size (starting at 0)
     *
     * @param keys array of items to add to indexing
     */
    public MapInt index(Object[] keys) {
        for (Object o : keys)
            if (!map.containsKey(o)) {
                map.put(o, map.size());
            }
        return this;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public Object[] sortedKeys() {
        Set<Object> s = map.keySet();
        Object[] array = s.toArray(new Object[s.size()]);
        Data.sort(array);
        return array;
    }
}
