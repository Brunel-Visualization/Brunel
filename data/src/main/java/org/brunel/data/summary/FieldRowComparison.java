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

package org.brunel.data.summary;

import org.brunel.data.Data;
import org.brunel.data.Field;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Details on how to compare rows
 */
public class FieldRowComparison implements Comparator<Integer> {
    private final boolean[] ascending;
    private final boolean rowsBreakTies;
    private final int n;
    private final Field[] fields;

    public FieldRowComparison(Field[] fields, boolean[] ascending, boolean rowsBreakTies) {
        this.fields = fields;
        this.ascending = ascending;
        this.rowsBreakTies = rowsBreakTies;
        this.n = ascending == null ? fields.length : ascending.length;
    }

    public int compare(Integer a, Integer b) {
        for (int i = 0; i < n; i++) {
            int n = fields[i].compareRows(a, b);
            // If descending, change sort order
            if (n != 0) return ascending != null && !ascending[i] ? -n : n;
        }
        return rowsBreakTies ? (a - b) : 0;
    }

    public boolean isEmpty() {
        return fields.length == 0;
    }

    public int[] makeSortedOrder() {
        int n = fields[0].rowCount();
        Integer[] items = new Integer[n];
        for (int i = 0; i < n; i++) items[i] = i;
        Arrays.sort(items, this);
        return Data.toPrimitive(items);
    }
}
