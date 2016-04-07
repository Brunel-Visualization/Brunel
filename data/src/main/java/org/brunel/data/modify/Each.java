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
import org.brunel.data.Fields;
import org.brunel.data.util.ItemsList;

import java.util.ArrayList;
import java.util.List;

/**
 * This transform takes data and converts a single row with multiple items into multiple rows each
 * with a single item.
 */
public class Each extends DataOperation {

    /*
     * Command is a list of fields to use to split rows
     */
    public static Dataset transform(Dataset base, String command) {
        // Apply successively to each usable field
        for (String s : strings(command, ';')) {
            Field f = base.field(s);
            if (f.isProperty("list"))
                base = splitListsInField(base, f);
        }
        return base;
    }

    private static Dataset splitListsInField(Dataset base, Field target) {

        List<Integer> rows = new ArrayList<>();             // A list of the rows from the base
        List<Object> fieldValues = new ArrayList<>();       // A parallel list of the values of the field
        for (int i = 0; i < target.rowCount(); i++) {
            ItemsList list = (ItemsList) target.value(i);
            if (list == null) {
                fieldValues.add(null);                      // Add a single null value for this row
                rows.add(i);                                // Add a single index for this row
            } else for (int j=0; j<list.size(); j++) {
                fieldValues.add(list.get(j));               // For the j-th item,in the list, add the value
                rows.add(i);                                // Add the same index for each list case
            }
        }

        // Convert List<Integer> to int[]
        int[] idx = Data.toPrimitive(rows.toArray(new Integer[rows.size()]));

        Field[] results = new Field[base.fields.length];
        for (int i = 0; i < results.length; i++) {
            Field f = base.fields[i];
            if (f == target) {
                Object[] data = fieldValues.toArray(new Object[fieldValues.size()]);
                results[i] = Fields.makeColumnField(f.name, f.label, data);
            } else {
                results[i] = Fields.permute(f, idx, false);
            }
        }

        return base.replaceFields(results);

    }

}
