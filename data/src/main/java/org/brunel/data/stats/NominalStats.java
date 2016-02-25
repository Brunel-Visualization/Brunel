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

package org.brunel.data.stats;

import org.brunel.data.Field;
import org.brunel.data.util.MapInt;

public class NominalStats {

    public static void populate(Field f) {
        MapInt counts = new MapInt();
        int N = f.rowCount();

        // Create map of counts
        for (int i = 0; i < N; i++)
            counts.increment(f.value(i));

        f.set("n", N);
        f.set("unique", counts.size());
        f.set("valid", counts.getTotalCount());
        f.set("mode", counts.mode());

        Object[] naturalOrder;
        if (f.propertyTrue("categoriesOrdered")) {
            naturalOrder = f.categories();
        } else {
            if (f.name.equals("#selection")) {
                // The categories are as follows
                naturalOrder = new Object[]{Field.VAL_UNSELECTED, Field.VAL_SELECTED};
            } else {
                // Extract categories from the counts
                naturalOrder = counts.sortedKeys();
            }
            f.set("categories", naturalOrder);
        }

        f.set("categoryCounts", counts.getCounts(naturalOrder));
    }

    public static boolean creates(String key) {
        return "n".equals(key) || "mode".equals(key) || "unique".equals(key) || "valid".equals(key)
                || "categories".equals(key) || "categoryCounts".equals(key);
    }
}
