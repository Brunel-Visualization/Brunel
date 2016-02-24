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
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.util.ItemsList;

import java.util.ArrayList;
import java.util.List;

/**
 * This transform takes data and multiplies rows
 */
public class Each extends DataOperation {


    /*
     * Command is a list of fields to use to split rows
     */
    public static Dataset transform(Dataset base, String command) {
        // Step through all valid fields
        String[] fieldNames = parts(command);
        if (fieldNames == null) return base;

        // Apply successively to each usable field
        for (String s : fieldNames) {
            Field f = base.field(s);
            if (f.property("listCategories") != null)
                base = splitFieldValues(base, f);
        }

        return base;

    }

    private static Dataset splitFieldValues(Dataset base, Field target) {

       ItemsList nulls = new ItemsList(new Object[1], null);

        List<Integer> index = new ArrayList<Integer>();         // For the non-target fields
        List<Object> splitValues = new ArrayList<Object>();     // For the target field
        for (int i = 0; i < target.rowCount(); i++) {
            ItemsList list = (ItemsList) target.value(i);
            if (list == null) list = nulls;                // Treat a null as a list with a single null item
            for (Object o : list) {
                splitValues.add(o);                         // Add the actual value
                index.add(i);                               // Repeat the index for each list case
            }
        }

        // Convert List<Integer> to int[]
        int[] idx = Data.toPrimitive(index.toArray(new Integer[index.size()]));

        Field[] results = new Field[base.fields.length];
        for (int i = 0; i < results.length; i++) {
            Field f = base.fields[i];
            if (f == target) {
                Object[] data = splitValues.toArray(new Object[splitValues.size()]);
                results[i] = Data.makeColumnField(f.name, f.label, data);
            } else {
                results[i] = Data.permute(f, idx, false);
            }
        }

        return base.replaceFields(results);

    }

}
