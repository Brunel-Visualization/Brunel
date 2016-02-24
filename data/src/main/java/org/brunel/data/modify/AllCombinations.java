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

package org.brunel.data.modify;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.summary.FieldRowComparison;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by graham on 2/24/16.
 */
public class AllCombinations {
    private final Field[] fields;
    private final int xCount;
    private final int groupCount;
    private final int keyLength;
    private final int[] index;
    private final Object[][] categories;

    public AllCombinations(Field[] fields, int xCount, int groupCount) {
        this.fields = fields;
        this.xCount = xCount;
        this.groupCount = groupCount;
        this.keyLength = xCount + groupCount;           // These are used for the keys
        this.index = new int[keyLength];                // The index of the current row we are considering
        this.categories = makeFieldCategories();
    }

    private Object[][] makeFieldCategories() {
        Object[][] result = new Object[keyLength][];
        for (int i = 0; i < keyLength; i++) {
            Object[] objects = fields[i].categories();
            if (i < xCount)
                result[i] = objects;
            else if (i >= xCount) {
                result[i] = new Object[objects.length];
                for (int j = 0; j < objects.length; j++) result[i][j] = objects[objects.length - 1 - j];
            }
        }
        return result;
    }

    Field[] make() {

        int N = fields[0].rowCount();
        List<Object[]> rows = new ArrayList<Object[]>();
        Integer[] rowOrder = makeRowOrderForRealData();

        int dataIndex = 0;                                              // Which row of real data to use
        int nextRealRow = rowOrder[dataIndex];                          // The index of the next real row to use

        while (true) {
            Object[] row = makeKeyRow();
            boolean matched = false;
            while (matchesCurrent(row, nextRealRow)) {
                matched = true;
                rows.add(makeRealRow(nextRealRow));
                if (++dataIndex >= N) break;
                nextRealRow = rowOrder[dataIndex];
            }
            if (!matched) rows.add(row);
            if (!nextIndex()) break;
        }

//
//        int[] index = new int[keys.length];                    // Index to step through all combinations
//        while (index != null) {
//            Object[] row = makeGeneratedRow(fields, keyIndices, index);
//            boolean matched = false;
//            while (matchKeys(row, currentRow, keyIndices)) {
//                rows.add(currentRow);                                       // Add the matching row
//                currentRow = makeRealRow(fields, ++currentRowIndex);    // Try the next one
//                matched = true;
//            }
//            if (!matched) rows.add(row);                                    // only add fake row if there is no real row
//            index = nextIndex(keys, index);
//        }
//
        Field[] built = new Field[fields.length];
        for (int i = 0; i < fields.length; i++) {
            built[i] = Data.makeColumnField(fields[i].name, fields[i].label, extractColumn(rows, i));
            Data.copyBaseProperties(built[i], fields[i]);
        }
        return built;
    }

    private Integer[] makeRowOrderForRealData() {
        // We need descending order so stacking works bottom-up
        List<Integer> items = new ArrayList<Integer>();
        for (int i = 0; i < fields[0].rowCount(); i++) items.add(i);
        boolean[] ascending = new boolean[keyLength];
        for (int i = 0; i < ascending.length; i++) ascending[i] = i < xCount;
        FieldRowComparison comparison = new FieldRowComparison(fields, ascending, true);
        Collections.sort(items, comparison);
        return items.toArray(new Integer[items.size()]);
    }

    private Object[] extractColumn(List<Object[]> rows, int index) {
        Object[] result = new Object[rows.size()];
        for (int i = 0; i < result.length; i++) result[i] = rows.get(i)[index];
        return result;
    }

    // Generate a row only having values for the keys
    private Object[] makeKeyRow() {
        Object[] row = new Object[fields.length];
        // Step through all the key values and set those values. All non-key values will be missing
        for (int i = 0; i < keyLength; i++) {
            // The column at j has its value set to the category of the corresponding field with the current index
            row[i] = categories[i][index[i]];
        }
        return row;
    }

    // Copy data from columns into the row
    private Object[] makeRealRow(int index) {
        int n = fields.length;
        Object[] row = new Object[n];
        for (int i = 0; i < fields.length; i++)
            row[i] = fields[i].value(index);
        return row;
    }

    private boolean matchesCurrent(Object[] row, int dataRow) {
        for (int i = 0; i < keyLength; i++)
            if (Data.compare(row[i], fields[i].value(dataRow)) != 0) return false;
        return true;
    }

    // Increment the index, returning false if we are done
    private boolean nextIndex() {
        for (int p = index.length - 1; p >= 0; p--) {
            int max = fields[p].categories().length;        // the max value this index can be
            if (++index[p] < max) return true;
            index[p] = 0;                                   // not successful, set to zero and loop to next position
        }
        return false;                                       // Ran out of indices
    }

}
