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
import org.brunel.data.util.ItemsList;

import java.util.HashMap;
import java.util.Map;

/**
 * Add facet information to a dataset.
 * This is currently not used in the system
 */
public class ApplyJoinKeys extends DataOperation {

    /* Adds a field "#facetKey" */
    public static Dataset transformParent(Dataset data) {
        Field field = Data.makeIndexingField("#facetKey", null, data.rowCount());
        field.set("key", true);
        field.set("numeric", false);
        return Data.appendFields(data, new Field[]{field});
    }

    /* Adds a field "#facetReference" referring back to the facetKey in the parent data */
    public static Dataset transformChild(Dataset data, Dataset outerData) {
        Field reference = Data.makeColumnField("#facetReference", null, makeReferenceData(outerData, data.field("#row")));
        reference.set("numeric", false);
        return Data.appendFields(data, new Field[]{reference});
    }

    private static Object[] makeReferenceData(Dataset referenceTarget, Field tableKey) {
        // Make a map from the original rows to the rows of the outer table
        Map<Integer, Integer> originalRowsToTargetRows = new HashMap<Integer, Integer>();
        Field targetRows = referenceTarget.field("#row");   // The rows from the target table
        for (int i = 0; i < targetRows.rowCount(); i++) {
            Object o = targetRows.value(i);
            // The item is either an integer (for a simple table), or an ItemList of integers (for a summarized table)
            if (o instanceof ItemsList) {
                for (Object v : ((ItemsList) o))
                    originalRowsToTargetRows.put((Integer) v, i + 1);
            } else {
                originalRowsToTargetRows.put((Integer) o, i + 1);
            }
        }

        Integer[] referenceValues = new Integer[tableKey.rowCount()];
        for (int i = 0; i < referenceValues.length; i++) {
            Object o = tableKey.value(i);
            Integer referenceTargetRow;
            if (o instanceof ItemsList) {
                ItemsList list = (ItemsList) o;
                referenceTargetRow = originalRowsToTargetRows.get(list.get(0));
                // If we are aggregated, check that the aggregation matches OK
                for (Object v : list) {
                    Integer row = originalRowsToTargetRows.get(v);
                    if (!row.equals(referenceTargetRow))
                        throw new IllegalStateException("Incompatible aggregation structure when using 'inside' composition");
                }
            } else {
                referenceTargetRow = originalRowsToTargetRows.get(o);
            }
            referenceValues[i] = referenceTargetRow;
        }
        return referenceValues;
    }

}
