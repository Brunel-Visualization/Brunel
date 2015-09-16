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
import org.brunel.data.summary.DimensionField;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.summary.MeasureField;
import org.brunel.data.summary.SummaryValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Performs aggregation by defining a set of summarization commands:
 *
 * FIELD_NAME              -- a field to be used as a dimension (a factor or group)
 * FIELD_NAME : base       -- a field to be used as a dimension AND a base for percentages
 * FIELD_NAME : transform  -- a measure to use to transform the field (e.g. 'mean', 'count', ...)
 *
 * 'transform' is a statistical summary; one of sum, count, mode, median, mean, q1, q3, range, variance,
 * stddev, list (concatenates names together), iqr(interquartile range), range.
 *
 * Note that an empty field is legal for the count transform
 */
public class Summarize extends DataOperation {

    /*
        Each of the operations has as a key the new field to be created
        The operation is one of the following
            FIELD_NAME              -- a field to be used as a dimension (a factor or group)
            FIELD_NAME : base       -- a field to be used as a dimension AND a base for percentages
            FIELD_NAME : transform  -- a measure to use to transform the field (e.g. 'mean', 'count', ...)

            Note that an empty field is legal for the count transform
     */
    public static Dataset transform(Dataset base, String command) {
        if (base.rowCount() == 0) return base;
        Map<String, String> operations = map(command, "=");
        if (operations == null) return base;

        // Decode the operations into these collections
        List<MeasureField> measures = new ArrayList<MeasureField>();
        List<DimensionField> dimensions = new ArrayList<DimensionField>();
        List<Field> percentBase = new ArrayList<Field>();

        for (String name : operations.keySet()) {
            String[] values = operations.get(name).split(":");
            Field baseField = base.field(values[0].trim());
            if (values.length == 1) {
                dimensions.add(new DimensionField(baseField, name));
            } else if (values[1].trim().equals("base")) {
                dimensions.add(new DimensionField(baseField, name));
                percentBase.add(baseField);
            } else {
                MeasureField measureField = new MeasureField(baseField, name, values[1].trim());
                if (values.length > 2) {
                    // Add the option info in
                    measureField.option = values[2].trim();
                }
                measures.add(measureField);
            }
        }

        Collections.sort(measures);
        Collections.sort(dimensions);

        // ensure #count and #row are included
        if (operations.get("#count") == null) measures.add(new MeasureField(base.field("#count"), "#count", "sum"));
        if (operations.get("#row") == null) measures.add(new MeasureField(base.field("#row"), "#row", "list"));

        Summarize s = new Summarize(measures, dimensions, percentBase, base.rowCount());
        Field[] fields = s.make();
        return Data.replaceFields(base, fields);
    }

    private final List<MeasureField> measures;
    private final List<DimensionField> dimensions;
    private final List<Field> percentBase;
    private final boolean percentNeeded;
    private final int rowCount;

    public Summarize(List<MeasureField> measures, List<DimensionField> dimensions, List<Field> percentBase, int rowCount) {
        this.measures = measures;
        this.dimensions = dimensions;
        this.percentBase = percentBase;
        this.rowCount = rowCount;

        boolean percentNeeded = false;
        for (MeasureField m : measures) if (m.isPercent()) percentNeeded = true;
        this.percentNeeded = percentNeeded;
    }

    private Field[] make() {
        // Do not add slow percent base calculations unless needed

        // Assemble arrays of fields
        Field[] dimensionFields = getFields(dimensions);
        Field[] percentBaseFields = percentBase.toArray(new Field[percentBase.size()]);
        Field[] measureFields = getFields(measures);

        // The comparators to let us know when rows differ
        FieldRowComparison dimComparison = new FieldRowComparison(dimensionFields, null, false);
        FieldRowComparison percentBaseComparison = new FieldRowComparison(percentBaseFields, null, false);

        // group[row] gives the index of the summary group for row 'row'; 'groupCount' is the number of groups
        int[] group = new int[rowCount];
        int groupCount = makeGroups(group, dimComparison);

        // These are just like the summary groups, but only for the percent bases
        // The percent groups nest within each base group: rows with the same group have the same summary group also
        // we do not create these if they are not needed, for efficiency
        int[] percentGroup = percentNeeded ? new int[rowCount] : null;
        int percentGroupCount = percentNeeded ? makeGroups(percentGroup, percentBaseComparison) : 0;

        // Create the summary values for each group, and percentage sums
        SummaryValues[] summaries = new SummaryValues[groupCount];
        for (int i = 0; i < summaries.length; i++) summaries[i] = new SummaryValues(measureFields);
        double[][] percentSums = new double[percentGroupCount][measureFields.length];

        // Perform the Aggregation
        for (int row = 0; row < rowCount; row++) {
            SummaryValues value = summaries[group[row]];
            if (percentNeeded) {
                // If the group has not had percent sums set yet, then set it
                if (value.percentSums == null) value.percentSums = percentSums[percentGroup[row]];
                // Then add the values to the percentage count
                for (int i = 0; i < measureFields.length; i++) {
                    if (measures.get(i).isPercent()) {
                        Double v = Data.asNumeric(measureFields[i].value(row));
                        if (v != null) value.percentSums[i] += v;
                    }
                }
            }
            // Add the current row to that group, with the relevant 'sums' for percentages
            value.rows.add(row);
        }

        Object[][] dimData = new Object[dimensions.size()][groupCount];
        Object[][] measureData = new Object[measures.size()][groupCount];

        for (int g = 0; g < groupCount; g++) {
            SummaryValues values = summaries[g];
            int originalRow = values.firstRow();
            // Set the dimension values
            for (int i = 0; i < dimensions.size(); i++)
                dimData[i][g] = dimensionFields[i].value(originalRow);
            // Set the measure values
            for (int i = 0; i < measures.size(); i++) {
                MeasureField m = measures.get(i);
                measureData[i][g] = values.get(i, m);
            }
        }

        // Assemble fields
        Field[] fields = new Field[dimData.length + measureData.length];
        for (int i = 0; i < dimData.length; i++) {
            DimensionField f = dimensions.get(i);
            fields[i] = Data.makeColumnField(f.rename, f.label(), dimData[i]);
            setProperties(fields[i], f.field, null);
        }
        for (int i = 0; i < measureData.length; i++) {
            MeasureField m = measures.get(i);
            Field result = Data.makeColumnField(m.rename, m.label(), measureData[i]);
            setProperties(result, m.field, m.measureFunction);
            result.set("summary", m.measureFunction);
            fields[dimData.length + i] = result;
        }
        return fields;
    }

    private Field[] getFields(List<? extends DimensionField> list) {
        Field[] result = new Field[list.size()];
        for (int i = 0; i < result.length; i++) result[i] = list.get(i).field;
        return result;
    }

    private int makeGroups(int[] group, FieldRowComparison dimComparison) {
        int[] order = dimComparison.makeSortedOrder(rowCount);
        int currentGroup = 0;
        for (int i = 0; i < group.length; i++) {
            // If the comparison indicates the dimensions are different, move to a new group
            if (i > 0 && dimComparison.compare(order[i], order[i - 1]) != 0)
                currentGroup++;
            group[order[i]] = currentGroup;
        }
        return currentGroup + 1;
    }

    /* Copy the relevant detail over and set properties */
    private void setProperties(Field to, Field from, String summary) {
        if (summary == null || summary.equals("mode"))
            // Copied directly from the 'from' field
            Data.copyBaseProperties(to, from);
        else {
            if (!summary.equals("count") && !summary.equals("valid") && !summary.equals("unique"))
                Data.copyBaseProperties(to, from);
            to.set("numeric", !summary.equals("list") && !summary.equals("shorten"));
        }
    }
}
