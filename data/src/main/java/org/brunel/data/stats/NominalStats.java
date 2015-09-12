/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.data.stats;

import org.brunel.data.Data;
import org.brunel.data.Field;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NominalStats {

    public static void populate(Field f) {
        // Create map of counts
        Map<Object, Integer> count = new HashMap<Object, Integer>();
        Set<Object> modes = new HashSet<Object>();
        int N = f.rowCount();

        int maxCount = 0;
        int valid = 0;
        for (int i = 0; i < N; i++) {
            Object o = f.value(i);
            if (o == null) continue;
            valid ++;
            Integer c = count.get(o);
            int value = c == null ? 1 : c + 1;
            count.put(o, value);

            // Check for mode -- if this is more numerous than the rest, clear the list of modes
            // If at least as numerous, add the item to the list of modes
            if (value > maxCount) modes.clear();
            if (value >= maxCount) {
                modes.add(o);
                maxCount = value;
            }

        }

        f.set("n", N);
        f.set("unique", count.size());
        f.set("valid", valid);

        if (modes.isEmpty()) {
            f.set("mode", null);
        } else {
            // Set the mode to be the middle of the sorted list of modes
            Object[] sortedModes = modes.toArray(new Object[modes.size()]);
            Data.sort(sortedModes);
            f.set("mode", sortedModes[(int) ((sortedModes.length - 1) / 2)]);
        }

        Object[] naturalOrder;
        if (f.name.equals("#selection")) {
            // For selection data, ensure we ALWAYS have the two categories added in
            if (!count.containsKey(Field.VAL_UNSELECTED)) count.put(Field.VAL_UNSELECTED, 0);
            if (!count.containsKey(Field.VAL_SELECTED)) count.put(Field.VAL_SELECTED, 0);
            // The categories are as follows
            naturalOrder = new Object[]{Field.VAL_UNSELECTED, Field.VAL_SELECTED};
        } else {
            // Extract categories from the counts
            Set<Object> cats = count.keySet();
            naturalOrder = cats.toArray(new Object[cats.size()]);
            Data.sort(naturalOrder);
        }

        f.set("categories", naturalOrder);
        int[] counts = new int[naturalOrder.length];
        for (int i = 0; i < naturalOrder.length; i++)
            counts[i] = count.get(naturalOrder[i]);
        f.set("categoryCounts", counts);

    }

    public static boolean creates(String key) {
        return "n".equals(key) || "mode".equals(key) || "unique".equals(key) || "valid".equals(key)
                || "categories".equals(key) || "categoryCounts".equals(key);
    }
}
