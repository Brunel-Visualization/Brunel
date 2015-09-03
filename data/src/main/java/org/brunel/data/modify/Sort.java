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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This transform sorts a dataset into an order determined bya  set of fields.
 * Each field can be defined as "increasing" or "decreasing" and the order of fields is important!
 *
 * This class may sort categories of data if the data type allows it (i.e. it is categorical, not ordinal)
 * It will always sort the rows of the data (so a PATH element will use that order, for example)
 */
public class Sort extends DataOperation {

    public static Dataset transform(Dataset base, String command) {
        return doSort(base, command, true);
    }

    private static Dataset doSort(Dataset base, String command, boolean sortCategories) {
        String[] sortFields = parts(command);
        if (sortFields == null) return base;
        // Build the dimensional information
        Field[] dimensions = getFields(base, sortFields);
        boolean[] ascending = getAscending(dimensions, sortFields);

        // Sort the rows to get the new row order
        int[] rowOrder = new FieldRowComparison(dimensions, ascending, true).makeSortedOrder(base.rowCount());


        // Ensure that any data binned to the "..." catch-all category is moved to the end
        for (int i = base.fields.length - 1; i >= 0; i--) {
            Field f = base.fields[i];
            if (f.hasProperty("binned") && f.preferCategorical()) rowOrder = moveCatchAllToEnd(rowOrder, f);
        }

        Field[] fields = new Field[base.fields.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = Data.permute(base.fields[i], rowOrder, true);
            if (sortCategories && !fields[i].ordered())
                fields[i].setCategories(categoriesFromData(fields[i]));
        }

        return Data.replaceFields(base, fields);
    }

    private static Field[] getFields(Dataset base, String[] names) {
        Field[] fields = new Field[names.length];
        for (int i = 0; i < fields.length; i++) {
            String name = names[i].split(":")[0];
            fields[i] = base.field(name.trim());
            if (fields[i] == null) {
                throw new IllegalArgumentException("Could not find field: " + name);
            }
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
            } else
                ascending[i] = !dimensions[i].hasProperty("numeric");
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

    private static Object[] categoriesFromData(Field field) {
        List<Object> cats = new ArrayList<Object>();
        Set<Object> seen = new HashSet<Object>();
        for (int i = 0; i < field.rowCount(); i++) {
            Object o = field.value(i);
            if (o != null && seen.add(o)) cats.add(o);
        }
        return cats.toArray(new Object[cats.size()]);

    }

    static Dataset transformRows(Dataset base, String command) {
        return doSort(base, command, false);
    }

}
