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
import org.brunel.data.Fields;

/**
 * The command may be "", which means no operation.
 * Otherwise, it is of the format "field:count". The field must be numeric, otherwise it is an error
 * It is used as the "size" by which the rows are divided out. It is very commonly just #count
 * The end result will be a Dataset with exactly the requested number of rows (which should be > 0)
 */
public class SetRowCount extends DataOperation {

    /*
        Each of the operations has as a key the new field to be created
        The operation is one of the following
            FIELD_NAME              -- a field to be used as a dimension (a factor or group)
            FIELD_NAME : base       -- a field to be used as a dimension AND a base for percentages
            FIELD_NAME : transform  -- a measure to use to transform the field (e.g. 'mean', 'count', ...)

            Note that an empty field is legal for the count transform
     */
    public static Dataset transform(Dataset base, String command) {
        if (base.rowCount() == 0 || command.isEmpty()) return base;
        String[] parts = DataOperation.strings(command, ',');
        return new SetRowCount(base, base.field(parts[0]), Integer.parseInt(parts[1])).make();
    }

    private final Dataset base;
    private final Field sizeField;
    private final int N;

    private SetRowCount(Dataset base, Field sizeField, int N) {
        this.base = base;
        this.sizeField = sizeField;
        if (!sizeField.isNumeric())
            throw new IllegalArgumentException("Cannot set rows based on a non-numeric field");
        this.N = N;
    }

    private Dataset make() {
        if (base.rowCount() < N) return addRows();
        return base;
    }

    private Dataset addRows() {
        int n = base.rowCount();                    // The current numebr of rows

        // This is how many replications we'd really like, as fractional and exact
        double[] fractional = new double[n];
        int[] replications = new int[fractional.length];
        int calculatedN = 0;
        double total = sizeField.numProperty("mean") * sizeField.numProperty("n");
        for (int i = 0; i < n; i++) {
            Double value = Data.asNumeric(sizeField.value(i));
            fractional[i] = value == null ? 0 : value * N / total;
            replications[i] = (int) Math.round(fractional[i]);
            calculatedN += replications[i];
        }

        // If rounding caused too many rows, repeatedly reduce the result row that has the least excess
        while (calculatedN > N) {
            int least = 0;
            for (int i = 1; i < n; i++)
                if (fractional[i] - replications[i] < fractional[least] - replications[least])
                    least = i;
            replications[least]--;
            calculatedN--;
        }

        // If rounding caused too few rows, repeatedly increase the result row that has the least deficit
        while (calculatedN < N) {
            int most = 0;
            for (int i = 1; i < n; i++)
                if (fractional[i] - replications[i] > fractional[most] - replications[most])
                    most = i;
            replications[most]++;
            calculatedN++;
        }

        // Build the re-indexing
        int[] rowMap = new int[N];
        int targetRow = 0, baseRow = 0;
        while (targetRow<N) {
            for (int i=0; i<replications[baseRow]; i++) rowMap[targetRow++] = baseRow;
            baseRow++;
        }

        // Create the replicated fields
        Field[] newFields = new Field[base.fields.length];
        for (int i=0; i<newFields.length; i++)
            newFields[i] = Fields.permute(base.fields[i], rowMap, false);

        // Return the new result
        return base.replaceFields(newFields);

    }

}
