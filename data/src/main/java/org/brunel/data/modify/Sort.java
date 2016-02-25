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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This transform sorts a dataset into an order determined bya  set of fields.
 * Each field can be defined as "increasing" or "decreasing" and the order of fields is important!
 *
 * This class may sort categories of data if the data type allows it (i.e. it is categorical, not ordinal)
 * It will always sort the rows of the data (so a PATH element will use that order, for example)
 */
public class Sort extends DataOperation {

    public static Dataset transform(Dataset base, String command, boolean sortCategories) {
        String[] sortFields = strings(command, ';');
        if (sortFields.length == 0) return base;
        // Build the dimensional information
        Field[] dimensions = getFields(base, sortFields);
        boolean[] ascending = getAscending(dimensions, sortFields);

        // Sort the rows to get the new row order
        int[] rowOrder = new FieldRowComparison(dimensions, ascending, true).makeSortedOrder(base.rowCount());

        // Ensure that any data binned to the "..." catch-all category is moved to the end
        for (int i = base.fields.length - 1; i >= 0; i--) {
            Field f = base.fields[i];
            if (f.isBinned() && f.preferCategorical()) rowOrder = moveCatchAllToEnd(rowOrder, f);
        }

        Field[] fields = new Field[base.fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field field = base.fields[i];
            fields[i] = Fields.permute(field, rowOrder, true);
            if (!field.ordered() && sortCategories) {
                Object[] newCategoryOrder = makeOrder(field, dimensions, ascending);
                fields[i].setCategories(newCategoryOrder);
            }
        }
        return base.replaceFields(fields);
    }

    private static Object[] makeOrder(Field field, Field[] dimensions, boolean[] ascending) {

        // Map from field categories to rows for that field
        Map<Object, List<Integer>> categorySums = new HashMap<Object, List<Integer>>();
        for (int i = 0; i < field.rowCount(); i++) {
            Object category = field.value(i);
            if (category == null) continue;
            List<Integer> value = categorySums.get(category);
            if (value == null) {
                value = new ArrayList<Integer>();
                categorySums.put(category, value);
            }
            value.add(i);
        }

        // For each row, create a value
        Object[] categories = field.categories();
        int n = categories.length;
        Object[][] dimensionData = new Object[dimensions.length][n];
        for (int r = 0; r < n; r++) {
            List<Integer> value = categorySums.get(categories[r]);
            for (int i = 0; i < dimensions.length; i++) {
                if (dimensions[i].isNumeric())
                    dimensionData[i][r] = sum(dimensions[i], value);
                else
                    dimensionData[i][r] = mode(dimensions[i], value);
            }
        }
        Field[] summaries = new Field[dimensions.length];
        for (int i = 0; i < dimensions.length; i++) {
            summaries[i] = Fields.makeColumnField("", null, dimensionData[i]);
            if (dimensions[i].isNumeric()) summaries[i].setNumeric();
        }
        int[] order = new FieldRowComparison(summaries, ascending, true).makeSortedOrder(n);
        Object[] result = new Object[n];
        for (int i = 0; i < n; i++) result[i] = categories[order[i]];
        return result;

    }

    private static Object sum(Field field, List<Integer> rows) {
        double sum = 0;
        for (int i : rows) {
            Double v = Data.asNumeric(field.value(i));
            if (v != null) sum += v;
        }
        return sum;
    }

    private static Object mode(Field field, List<Integer> rows) {
        Object mode = null;
        int max = 0;
        Map<Object, Integer> count = new HashMap<Object, Integer>();
        for (int i : rows) {
            Object v = field.value(i);
            Integer c = count.get(v);
            if (c == null) c = 0;
            if (++c > max) {
                max = c;
                mode = v;
            }
            count.put(v, c);
        }
        return mode;
    }

    /* Convert an order (possibly with ties) into a ranking for the original rows */
    static double[] makeRowRanking(int[] order, FieldRowComparison comparison) {
        double[] ranks = new double[order.length];
        int runStart = 0;
        while (runStart < order.length) {
            // Create a run [runStart, runEnd-1] of identical values
            int runEnd = runStart + 1;
            while (runEnd < order.length && comparison.compare(order[runStart], order[runEnd]) == 0) runEnd++;
            double v = (runEnd + runStart + 1) / 2.0;

            // Set the ranks in the result array, incrementing runStart to the runEnd
            while (runStart < runEnd) ranks[order[runStart++]] = v;
        }
        return ranks;
    }

    private static Field[] getFields(Dataset base, String[] names) {
        Field[] fields = new Field[names.length];
        for (int i = 0; i < fields.length; i++) {
            String name = names[i].split(":")[0];
            fields[i] = base.field(name.trim());
            if (fields[i] == null)
                throw new IllegalArgumentException("Could not find field: " + name);
        }
        return fields;
    }

    private static boolean[] getAscending(Field[] dimensions, String[] names) {
        boolean[] ascending = new boolean[dimensions.length];
        for (int i = 0; i < ascending.length; i++) {
            String[] parts = names[i].split(":");
            if (parts.length > 1) {
                String p = parts[1].trim();
                if (p.equalsIgnoreCase("ascending")) ascending[i] = true;
                else if (p.equalsIgnoreCase("descending")) ascending[i] = false;
                else throw new IllegalArgumentException("Sort options must be 'ascending' or 'descending'");
            } else {
                // Dates and categories default to ascending. General numeric values default to descending
                ascending[i] = dimensions[i].isDate() || !dimensions[i].isNumeric();
            }
        }
        return ascending;
    }

    private static int[] moveCatchAllToEnd(int[] order, Field f) {
        int[] result = new int[order.length];
        List<Integer> atEnd = new ArrayList<Integer>();
        int at = 0;
        for (int j : order) {
            if ("\u2026".equals(f.value(j))) atEnd.add(j);
            else result[at++] = j;
        }
        for (Integer i : atEnd) result[at++] = i;
        return result;
    }

    // Each row gets the sum rank
    static Object[] categoriesFromRanks(Field field, double[] rowRanking) {
        double n = field.rowCount();                                                // # rows
        Object[] categories = field.categories();                                   // categories
        int[] counts = (int[]) field.property("categoryCounts");                    // category counts
        Double[] means = new Double[counts.length];                                 // sums of ranks
        for (int i = 0; i < means.length; i++) means[i] = i / 100.0 / means.length; // Bias towards current order

        // Map categories to an index
        Map<Object, Integer> index = new HashMap<Object, Integer>();
        for (Object o : categories) index.put(o, index.size());

        // Sum ranks for this category
        for (int i = 0; i < n; i++) {
            Object o = field.value(i);
            if (o == null) continue;
            int idx = index.get(o);
            means[idx] += rowRanking[i] / counts[idx];
        }

        // Get the order (biggest first)
        Integer[] which = Data.order(means, true);

        // Build the sorted category list
        Object[] cats = new Object[which.length];
        for (int i = 0; i < which.length; i++)
            cats[i] = categories[which[i]];

        return cats;
    }
}
