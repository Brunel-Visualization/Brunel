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
import org.brunel.data.Fields;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands out a set of data so it has all combinations of the X and group fields.
 * This ensures that stacking liens and areas works, as they require that each one have
 * the full range of data.
 */
public class AllCombinations {
    private final Field[] fields;               // All fields, in the order X, groups, other
    private final int xCount;                   // The number of 'x' fields (to be sorted ascending)
    private final int keyLength;                // The first 'keyLength' fields are keys
    private final Object[][] categories;        // The field categories in the order we want to use them
    private final int[] index;                  // multi-dimensional index into the categories

    public AllCombinations(Field[] fields, int xCount, int groupCount) {
        this.fields = fields;
        this.xCount = xCount;
        this.keyLength = xCount + groupCount;           // These are used for the keys
        this.index = new int[keyLength];                // The index of the current row we are considering
        this.categories = makeFieldCategories();
    }

    private Object[][] makeFieldCategories() {
        Object[][] result = new Object[keyLength][];
        for (int i = 0; i < keyLength; i++) {
            Object[] objects = fields[i].categories();
            // The X fields keep the order, the group ones need reversed order
            if (i < xCount)
                result[i] = objects;
            else if (i >= xCount) {
                result[i] = new Object[objects.length];
                for (int j = 0; j < objects.length; j++)
                    result[i][j] = objects[objects.length - 1 - j];
            }
        }
        return result;
    }

    /**
     * Build new fields giving all combinations of the key fields
     * @return combined data
     */
    Field[] make() {
        // Create the order in which the real data will be encountered
        Integer[] rowOrder = Stack.makeStackDataOrder(fields, keyLength, xCount);

        int dataIndex = 0;                                              // Which row of real data to use
        List<Object[]> rows = new ArrayList<Object[]>();                // The resulting rows we will use

        while (true) {
            Object[] row = makeKeyRow();                                // Only key values in this
            boolean matched = false;                                    // Did we match to real data?
            while (matchesCurrent(row, rowOrder, dataIndex)) {          // .. test to see if we match
                matched = true;                                         // .. and remember if we did
                rows.add(makeRealRow(rowOrder[dataIndex++]));           // add the real row to the results
            }
            if (!matched) rows.add(row);                                // If we didn't match, add the 'key only' row
            if (!nextIndex()) break;                                    // Done when no more generated combinations
        }

        // Convert list of data to real rows
        Field[] built = new Field[fields.length];
        for (int i = 0; i < fields.length; i++) {
            built[i] = Fields.makeColumnField(fields[i].name, fields[i].label, extractColumn(rows, i));
            Fields.copyBaseProperties(fields[i], built[i]);
        }
        return built;
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

    private boolean matchesCurrent(Object[] row, Integer[] dataRowOrder, int dataIndex) {
        if (dataIndex >= dataRowOrder.length) return false;             // Past the end -- no match
        int dataRow = dataRowOrder[dataIndex];
        for (int i = 0; i < keyLength; i++)
            if (Data.compare(row[i], fields[i].value(dataRow)) != 0) return false;
        return true;
    }

    // Increment the index, returning false if we are done
    private boolean nextIndex() {
        for (int p = index.length - 1; p >= 0; p--) {
            if (++index[p] < categories[p].length) return true; // Done if we can increment and not overflow
            index[p] = 0;                                       // Reset this dimension and try the next dim
        }
        return false;                                           // Ran out of dimensions -- must be done
    }

}
