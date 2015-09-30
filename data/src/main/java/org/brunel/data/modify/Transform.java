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
import org.brunel.data.auto.Auto;
import org.brunel.data.auto.NumericScale;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;

import java.util.HashMap;
import java.util.Map;

/**
 * This transform takes data and bins/ranks or odes another non-sumamarizing transformation
 * on the fields as given
 * The fields are given in the command as semi-colon separated items. Each item has the form a=b
 * which means the field a will have the transform b applied to it. The transforms are
 *
 * bin  -- with an optional parameter for
 * each field determining the desired number of bins. For example "salary=bin; age=bin:10" will ask for standard
 * binning for salary, and about 10 bins for age.
 * rank -- ranks the data, optional parameter "descending" has the largest as #1
 * inner/outer - keeps or removes data that lies within the inter-quartile range. An optional integer parameter gives the
 * percentage of data we would expect to keep or remove if the data were normal (so inner:5% would remove the outer 5%)
 *
 * Note that this transform does not aggregate the data -- it replaces fields with transformed values for each field.
 * Only inner/outer modify the actual data set values, removing rows
 */
public class Transform extends DataOperation {

    public static Dataset transform(Dataset base, String command) {
        if (base.rowCount() == 0) return base;
        Map<String, String> operations = map(command, "=");
        if (operations == null) return base;

        Field[] fields = new Field[base.fields.length];
        for (int i = 0; i < fields.length; i++)
            fields[i] = modify(base.fields[i], operations.get(base.fields[i].name));

        return base.replaceFields(fields);
    }

    /*
        Replace the base field with the modified version (if one is needed)
     */
    private static Field modify(Field field, String operation) {
        if (operation == null) return field;
        String[] parts = operation.split(":");
        String name = parts[0].trim();
        String option = parts.length > 1 ? parts[1].trim() : null;

        if (name.equals("bin")) {
            int desiredBinCount = option == null ? -1 : Integer.parseInt(option);    // -1 means the default
            return bin(field, desiredBinCount);
        } else if (name.equals("rank")) {
            return rank(field, "ascending".equals(option));
        } else {
            // inner, outer, top and bottom are all filtering operations
            return field;
        }
    }

    private static Field rank(Field f, boolean ascending) {
        int N = f.rowCount();

        // Sort the rows to get the new row order
        FieldRowComparison comparison = new FieldRowComparison(new Field[]{f}, new boolean[]{ascending}, true);
        int[] order = comparison.makeSortedOrder(N);

        Object[] ranks = new Object[N];                            // We will put the ranks in here
        int p = 0;                                                  // Step through runs of same items
        while (p < N) {
            int rowP = order[p];
            int q = p + 1;
            while (q < N && f.compareRows(rowP, order[q]) == 0) q++;  // Set q to be just past the end of a run
            for (int i = p; i < q; i++)
                ranks[order[i]] = (p + q + 1) / 2.0;  // All tied ranks get the same averaged value
            p = q;
        }
        Field result = Data.makeColumnField(f.name, f.label, ranks);// New data
        result.set("numeric", true);                        // Which is numeric
        return result;
    }

    /**
     * Bin the given field
     *
     * @param f               field to bin
     * @param desiredBinCount number of bins we would like (-1 for default)
     * @return A binning with near the desired number of bins
     */
    public static Field bin(Field f, int desiredBinCount) {
        Field field = f.preferCategorical() ? binCategorical(f, desiredBinCount) : binNumeric(f, desiredBinCount);
        field.set("binned", true);
        return field;
    }

    private static Field binCategorical(Field f, int desiredBinCount) {
        if (desiredBinCount < 1) desiredBinCount = 7;               // 7 is our default
        Object[] categories = f.categories();
        if (categories.length <= desiredBinCount) return f;         // We do not need to bin ...

        // Get the categories in reverse order, so largest first
        Integer[] order = Data.order((int[]) f.property("categoryCounts"), false);

        // Create map form current names to the new names
        Map<Object, Object> newNames = new HashMap<Object, Object>();
        for (int i = 0; i < order.length; i++)
            newNames.put(categories[order[i]], i < desiredBinCount ? categories[order[i]] : "\u2026");

        Object[] data = new Object[f.rowCount()];
        for (int i = 0; i < data.length; i++) data[i] = newNames.get(f.value(i));

        return Data.makeColumnField(f.name, f.label, data);
    }

    private static Field binNumeric(Field f, int desiredBinCount) {
        NumericScale scale = Auto.makeNumericScale(f, true, new double[]{0, 0}, 0.0, desiredBinCount + 1, true);
        Double[] divisions = scale.divisions;
        boolean isDate = f.isDate();
        DateFormat dateFormat = isDate ? (DateFormat) f.property("dateFormat") : null;
        Range[] ranges = makeBinRanges(divisions, dateFormat, scale.granular);
        Object[] data = binData(f, divisions, ranges);
        Field result = Data.makeColumnField(f.name, f.label, data);
        if (f.isDate())
            result.set("date", true);       // We do not simply use the date format and unit -- different now!
        result.set("numeric", true);        // But it IS numeric!
        result.set("categories", ranges);   // Include all bins in the categories, not just those that exist
        return result;
    }

    private static Range[] makeBinRanges(Double[] divisions, DateFormat dateFormat, boolean nameByCenter) {
        Range[] ranges = new Range[divisions.length - 1];
        for (int i = 0; i < ranges.length; i++) {
            Double a = divisions[i];
            Double b = divisions[i + 1];
            ranges[i] = (dateFormat == null) ? Range.makeNumeric(a, b, nameByCenter)
                    : Range.makeDate(a, b, nameByCenter, dateFormat);
        }
        return ranges;
    }

    private static Object[] binData(Field f, Double[] divisions, Range[] ranges) {
        Object[] data = new Object[f.rowCount()];
        for (int i = 0; i < data.length; i++) {
            Double d = Data.asNumeric(f.value(i));
            if (d == null) continue;
            int n = Data.indexOf(d, divisions);
            data[i] = ranges[Math.min(n, ranges.length - 1)];
        }
        return data;
    }

}
