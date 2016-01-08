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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This transform converts a set of "y" values into a single "#values" field and adds another
 * field with the "y" field names that is given the special name "#series".
 * It takes a list of y values, comma separated, and a then a list of other fields that need to be in the result.
 * Any field not in either of those will be dropped.
 * Note that it is possible (and useful) to have a field both in the 'y' and 'other' lists
 */
public class ConvertSeries extends DataOperation {

    public static Dataset transform(Dataset base, String commands) {
        if (commands.isEmpty()) return base;

        // The first section consists of a list of 'y' values to be made into series and values
        // The second section is a list of fields to be preserved as-is
        String[] sections = parts(commands);

        String[] yFields = list(sections[0]);

        // This also handles the case when there is a range specified (empty yFields)
        if (yFields == null || yFields.length < 2) return base;

        // If there are no other fields, there is only one section
        String[] otherFields = addRequired(list(sections.length < 2 ? "" : sections[1]));

        /*
            We handle four different categories of field:
            VALUES:
                this will be replaced with the stacked Y fields
            SERIES:
                this is an indicator (by name) of which field the Y value came from
            Y FIELDS:
                all of these get converted to one field 'values', and they are stacked on top of each other in order
            REST:
                these will also be stacked using indexing so they org.brunel.app.match the Y values
         */

        int N = base.rowCount();            // The rows in the original data

        Field series = makeSeries(yFields, N);
        Field values = makeValues(yFields, base, N);
        int[] indexing = makeIndexing(yFields.length, N);

        List<Field> resultFields = new ArrayList<Field>();
        resultFields.add(series);
        resultFields.add(values);
        for (String fieldName : otherFields) {
            // The special fields have already been added
            if (fieldName.equals("#series") || fieldName.equals("#values")) continue;
            Field f = base.field(fieldName);
            resultFields.add(Data.permute(f, indexing, false));
        }

        Field[] fields = resultFields.toArray(new Field[resultFields.size()]);

        return base.replaceFields(fields);
    }

    private static String[] addRequired(String[] list) {
        // Ensure #count and #row are present
        List<String> result = new ArrayList<String>();
        if (list != null) Collections.addAll(result, list);
        if (!result.contains("#row")) result.add("#row");
        if (!result.contains("#count")) result.add("#count");
        return result.toArray(new String[result.size()]);
    }

    private static Field makeSeries(String[] names, int reps) {
        // Make a block that looks like 0,0,0,0,   1,1,1,1,   2,2,2,2   (for a dataset with four rows, three names)
        Field temp = Data.makeColumnField("#series", "Series", names);
        int[] blocks = new int[names.length * reps];
        for (int i = 0; i < names.length; i++)
            for (int j = 0; j < reps; j++) blocks[i * reps + j] = i;
        Field field = Data.permute(temp, blocks, false);
        field.setCategories(names);
        return field;
    }

    private static Field makeValues(String[] yNames, Dataset base, int n) {
        Field[] y = new Field[yNames.length];
        for (int i = 0; i < y.length; i++) y[i] = base.field(yNames[i]);
        Object[] data = new Object[y.length * n];
        for (int i = 0; i < y.length; i++)
            for (int j = 0; j < n; j++) data[i * n + j] = y[i].value(j);
        Field field = Data.makeColumnField("#values", Data.join(yNames), data);
        Data.copyBaseProperties(field, y[0]);  // Should use the numeric and date properties
        return field;
    }

    private static int[] makeIndexing(int m, int reps) {
        // Make a block that looks like 0,1,2,3   0,1,2,3,   0,1,2,3   (for a dataset with four rows, three names)
        int[] blocks = new int[m * reps];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < reps; j++) blocks[i * reps + j] = j;
        return blocks;
    }

}
