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

package org.brunel.data.modify;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.summary.FieldRowComparison;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This transform takes a single y field and produces two new fields; a lower and an upper value.
 * It does so by stacking values from zero and placing items that are at the same 'x' values on top of each other.
 * The command passed in has four semi-colon separated parts --
 * [1] The y field to use
 * [2] A comma-separated list of x fields
 * [3] A comma-separated list of the fields used as groups within each X value
 * [4] "true" means we will generate all combinations of x fields and groups, even if not present in the data
 */
public class Stack extends DataOperation {

    /**
     * Create data in stacked form
     *
     * @param base    data to transform
     * @param command command to stack using
     * @return transformed data set
     */
    public static Dataset transform(Dataset base, String command) {
        if (command.isEmpty()) return base;

        String[] p = parts(command);
        String yField = p[0];
        String[] x = list(p[1]);
        String[] aesthetics = list(p[2]);
        boolean full = p[3].equalsIgnoreCase("true");

        if (x == null) x = new String[0];
        if (aesthetics == null) aesthetics = new String[0];

        // We sort the data in the order: X first, then aesthetics, then Y (the last to break ties)
        Field[] keyFields = getFields(base.fields, x, aesthetics, new String[]{yField});

        // Get all fields, permuted so they are in the order required by the key
        Field[] allFields = orderRows(base, keyFields);

        // When we need full combinations, we expand out our base data
        if (full) {
            Field[] comboFields = getFields(allFields, x, aesthetics);  // X and aesthetic fields
            allFields = addAllCombinations(allFields, comboFields);     // Add in all combinations
        }

        // Make the stacking using the new ordered fields
        Field[] fields = makeStackedValues(allFields,
                getField(allFields, yField),
                getFields(allFields, x), full);
        return Data.replaceFields(base, fields);
    }

    private static Field[] addAllCombinations(Field[] baseFields, Field[] keys) {

        // Create an array that gives the indices of the keys into the base fields
        int[] keyIndices = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            for (int j = 0; j < baseFields.length; j++)
                if (baseFields[j] == keys[i]) keyIndices[i] = j;
        }

        List<Object[]> rows = new ArrayList<Object[]>();

        int currentRowIndex = 0;                                            // The next row of real data to use
        Object[] currentRow = makeRealRow(baseFields, currentRowIndex);     // And the actual values

        int[] index = new int[keys.length];                    // Index to step through all combinations
        while (index != null) {
            Object[] row = makeGeneratedRow(baseFields, keyIndices, index);
            boolean matched = false;
            while (matchKeys(row, currentRow, keyIndices)) {
                rows.add(currentRow);                                       // Add the matching row
                currentRow = makeRealRow(baseFields, ++currentRowIndex);    // Try the next one
                matched = true;
            }
            if (!matched) rows.add(row);                                    // only add fake row if there is no real row
            index = nextIndex(keys, index);
        }

        Field[] fields = new Field[baseFields.length];
        for (int i = 0; i < baseFields.length; i++) {
            fields[i] = Data.makeColumnField(baseFields[i].name, baseFields[i].label, extractColumn(rows, i));
            Data.copyBaseProperties(fields[i], baseFields[i]);
        }
        return fields;

    }

    private static Object[] extractColumn(List<Object[]> rows, int index) {
        Object[] result = new Object[rows.size()];
        for (int i = 0; i < result.length; i++) result[i] = rows.get(i)[index];
        return result;
    }

    private static Field getField(Field[] fields, String name) {
        for (Field f : fields) if (f.name.equals(name)) return f;
        throw new IllegalArgumentException("Could not find field: " + name);
    }

    private static Field[] getFields(Field[] fields, String[]... namesList) {
        List<Field> result = new ArrayList<Field>();
        for (String[] s : namesList)
            for (String fName : s) {
                fName = fName.trim();
                if (!fName.isEmpty()) result.add(getField(fields, fName));
            }
        return result.toArray(new Field[result.size()]);
    }

    // Generate a row only having values for the keys
    private static Object[] makeGeneratedRow(Field[] fields, int[] keyIndices, int[] index) {
        int n = fields.length;
        Object[] row = new Object[n];
        // Step through all the key values and set those values. All non-key values will be missing
        for (int i = 0; i < keyIndices.length; i++) {
            int j = keyIndices[i];
            // The column at j has its value set to the category of the corresponding field with the current index
            row[j] = fields[j].categories()[index[i]];
        }
        return row;
    }

    // Copy data from columns into the row
    private static Object[] makeRealRow(Field[] fields, int index) {
        if (index >= fields[0].rowCount()) return null;             // No more real rows!
        int n = fields.length;
        Object[] row = new Object[n];
        for (int i = 0; i < fields.length; i++)
            row[i] = fields[i].value(index);
        return row;
    }

    private static Field[] makeStackedValues(Field[] allFields, Field y, Field[] x, boolean full) {
        int N = y.rowCount();
        // The lower and upper values for each y field
        Object[][] bounds = new Object[2][N];

        // Running positive and negative stacks for the current X value
        double lastPositive = 0;
        double lastNegative = 0;
        FieldRowComparison rowComparison = new FieldRowComparison(x, null, false);
        for (int i = 0; i < N; i++) {
            Double v = Data.asNumeric(y.value(i));
            if (v == null) {
                // For full data we change missing values to zeroes, so stacking works on the complete data
                // If we do not need full data, we can just skip it
                if (full) v = 0.0;
                else continue;
            }

            // The data is sorted, so just need to reset the stacks when we move to a new x value
            if (i > 0 && rowComparison.compare(i, i - 1) != 0) {
                lastPositive = 0;
                lastNegative = 0;
            }

            if (v < 0) {
                bounds[0][i] = lastNegative;
                lastNegative += v;
                bounds[1][i] = lastNegative;
            } else {
                bounds[0][i] = lastPositive;
                lastPositive += v;
                bounds[1][i] = lastPositive;
            }
        }

        int n = allFields.length;
        Field[] fields = new Field[n + 2];
        for (int i = 0; i < n; i++) fields[i] = allFields[i];
        fields[n] = Data.makeColumnField(y.name + "$lower", y.label, bounds[0]);
        fields[n + 1] = Data.makeColumnField(y.name + "$upper", y.label, bounds[1]);
        Data.copyBaseProperties(fields[n], y);
        Data.copyBaseProperties(fields[n + 1], y);
        Arrays.sort(fields);
        return fields;
    }

    private static boolean matchKeys(Object[] a, Object[] b, int[] indices) {
        if (a == null || b == null) return false;
        for (int i : indices)
            if (Data.compare(a[i], b[i]) != 0) return false;
        return true;
    }

    // Increment the index, returning null if we are done
    private static int[] nextIndex(Field[] keys, int[] index) {
        for (int p = index.length - 1; p >= 0; p--) {
            int max = keys[p].categories().length;          // the max value this index can be
            if (++index[p] < max) return index;             // successful increment!
            index[p] = 0;                                   // not successful, set to zero and loop to next position
        }
        return null;
    }

    private static Field[] orderRows(Dataset base, Field[] keyFields) {
        Field[] baseFields = base.fields;
        int[] rowOrder = new FieldRowComparison(keyFields, null, true).makeSortedOrder(base.rowCount());
        Field[] fields = new Field[baseFields.length];
        for (int i = 0; i < baseFields.length; i++)
            fields[i] = Data.permute(baseFields[i], rowOrder, true);
        return fields;
    }

}
