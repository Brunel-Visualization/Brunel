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

package org.brunel.data.modify;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.Fields;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        String[] p = strings(command, ';');
        String yField = p[0];
        String[] x = strings(p[1], ',');
        String[] aesthetics = strings(p[2], ',');
        boolean full = p[3].equalsIgnoreCase("true");

        // We sort the data in the order: X first, then aesthetics, then Y (the last to break ties)
        Field[] keyFields = getFields(base.fields, x, aesthetics, new String[]{yField});

        // Get all fields, permuted so they are in the order required by the key
        // This also removes any rows with null keys
        Field[] allFields = makeStackOrderedFields(base, keyFields, x.length);

        // When we need full combinations, we expand out our base data
        if (full) allFields = new AllCombinations(allFields, x.length, aesthetics.length).make();

        // Make the stacking using the new ordered fields
        Field[] fields = makeStackedValues(allFields,
                getField(allFields, yField),
                getFields(allFields, x), full);

        return base.replaceFields(fields);
    }

    private static Field getField(Field[] fields, String name) {
        for (Field f : fields) if (f.name.equals(name)) return f;
        throw new IllegalArgumentException("Could not find field: " + name);
    }

    private static Field[] getFields(Field[] fields, String[]... namesList) {
        List<Field> result = new ArrayList<Field>();
        Set<Field> found = new HashSet<Field>();
        for (String[] s : namesList)
            for (String fName : s) {
                fName = fName.trim();
                if (!fName.isEmpty()) {
                    Field field = getField(fields, fName);
                    if (found.add(field)) result.add(field);
                }
            }
        return result.toArray(new Field[result.size()]);
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
        fields[n] = Fields.makeColumnField(y.name + "$lower", y.label, bounds[0]);
        fields[n + 1] = Fields.makeColumnField(y.name + "$upper", y.label, bounds[1]);
        Fields.copyBaseProperties(y, fields[n]);
        Fields.copyBaseProperties(y, fields[n + 1]);
        Arrays.sort(fields);
        return fields;
    }

    /**
     * This orders the field in a suitable way for stacking.
     * The fields are in the order x, groups, other
     * The rows are ordered the way we want stacking to work
     *
     * @param base        base data set
     * @param keyFields   those fields that are key fields
     * @param xFieldCount of the key fields, the number that are X fields
     * @return fields (in order) with rows (in order)
     */
    private static Field[] makeStackOrderedFields(Dataset base, Field[] keyFields, int xFieldCount) {
        Field[] baseFields = orderFields(base, keyFields);
        Integer[] rowOrder = makeStackDataOrder(baseFields, keyFields.length, xFieldCount);
        Field[] fields = new Field[baseFields.length];
        for (int i = 0; i < baseFields.length; i++)
            fields[i] = Fields.permute(baseFields[i], Data.toPrimitive(rowOrder), true);
        return fields;
    }

    public static Integer[] makeStackDataOrder(Field[] fields, int keyFieldCount, int xFieldCount) {
        List<Integer> items = new ArrayList<Integer>();
        int n = fields[0].rowCount();
        for (int i = 0; i < n; i++) {
            boolean valid = true;
            for (int j = 0; j < keyFieldCount; j++)
                if (fields[j].value(i) == null) valid = false;
            if (valid) items.add(i);
        }

        // We need descending order so stacking works bottom-up
        boolean[] ascending = new boolean[keyFieldCount];
        for (int i = 0; i < ascending.length; i++) ascending[i] = i < xFieldCount;
        FieldRowComparison comparison = new FieldRowComparison(fields, ascending, true);
        Collections.sort(items, comparison);
        return items.toArray(new Integer[items.size()]);
    }

    private static Field[] orderFields(Dataset base, Field[] keyFields) {
        Field[] baseFields = new Field[base.fields.length];

        Set<Field> used = new HashSet<Field>();
        for (int i = 0; i < keyFields.length; i++) {
            baseFields[i] = keyFields[i];
            used.add(keyFields[i]);
        }
        int at = keyFields.length;
        for (Field f : base.fields) if (!used.contains(f)) baseFields[at++] = f;
        return baseFields;
    }

}
