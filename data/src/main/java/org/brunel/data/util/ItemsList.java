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

package org.brunel.data.util;

import org.brunel.data.Data;

import java.util.ArrayList;
import java.util.Collections;

public class ItemsList extends ArrayList<Object> implements Comparable<ItemsList> {

    private final DateFormat dateFormat;            // Needed to format as dates
    private int displayCount = 12;                  // Number of items to display before going to ellipses

    public ItemsList(Object[] items, DateFormat df) {
        dateFormat = df;
        Collections.addAll(this, items);
    }

     public boolean equals(Object obj) {
        return this == obj || obj instanceof ItemsList && compareTo((ItemsList) obj) == 0;
    }

    public int compareTo(ItemsList o) {
        int n = Math.min(size(), o.size());
        for (int i = 0; i < n; i++) {
            int d = Data.compare(get(i), o.get(i));
            if (d != 0) return d;
        }
        return size() - o.size();
    }

    public void setDisplayCount(int displayCount) {
        this.displayCount = displayCount;
    }

    public String toString() {
        String s = "";
        int n = size();
        for (int i = 0; i < n; i++) {
            if (i > 0) s += ", ";
            // Check for overflowing display requested size
            if (i == displayCount-1 && n > displayCount)
                return s + "\u2026";
            Object v = get(i);
            if (dateFormat != null)
                s += dateFormat.format(Data.asDate(v));
            else {
                Double d = Data.asNumeric(v);
                if (d != null)
                    s += Data.formatNumeric(d, false);
                else
                    s += v.toString();
            }
        }
        return s;
    }

}
