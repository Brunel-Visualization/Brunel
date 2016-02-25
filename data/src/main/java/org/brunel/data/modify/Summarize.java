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
import org.brunel.data.summary.DimensionField;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.summary.MeasureField;
import org.brunel.data.summary.SummaryValues;
import org.brunel.data.Fields;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        List<String[]> operations = map(command, "=");
        if (operations == null) return base;

        // Decode the operations into these collections
        List<MeasureField> measures = new ArrayList<MeasureField>();
        List<DimensionField> dimensions = new ArrayList<DimensionField>();
        List<Field> percentBase = new ArrayList<Field>();

        boolean containsCount = false;
        boolean containsRow = false;

        for (String[] op : operations) {
            if (op[0].equals("#count")) containsCount = true;
            if (op[0].equals("#row")) containsRow = true;
            String[] values = op[1].split(":");
            Field baseField = base.field(values[0].trim());
            if (values.length == 1) {
                dimensions.add(new DimensionField(baseField, op[0]));
            } else if (values[1].trim().equals("base")) {
                dimensions.add(new DimensionField(baseField, op[0]));
                percentBase.add(baseField);
            } else {
                MeasureField measureField = new MeasureField(baseField, op[0], values[1].trim());
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
        if (!containsCount) measures.add(new MeasureField(base.field("#count"), "#count", "sum"));
        if (!containsRow) measures.add(new MeasureField(base.field("#row"), "#row", "list"));

        Summarize s = new Summarize(measures, dimensions, percentBase, base.rowCount());
        Field[] fields = s.make();

        return base.replaceFields(fields);
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
        for (int i = 0; i < summaries.length; i++)
            summaries[i] = new SummaryValues(measureFields, percentBaseFields, dimensionFields);
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
            fields[i] = Fields.makeColumnField(f.rename, f.label(), dimData[i]);
            Fields.copyBaseProperties(fields[i], f.field);
        }
        for (int i = 0; i < measureData.length; i++) {
            MeasureField m = measures.get(i);
            Field result = Fields.makeColumnField(m.rename, m.label(), measureData[i]);
            setProperties(m.measureFunction, result, m.field);
            result.set("summary", m.measureFunction);
            if (m.field != null) result.set("originalLabel", m.field.label);
            fields[dimData.length + i] = result;
        }
        return fields;
    }

    private void setProperties(String f, Field to, Field src) {
        // Nothing to set for a list
        if (f.equals("list")) return;

        // These are numeric, but do not preserve properties
        if (f.equals("count")|| f.equals("percent") || f.equals("valid") || f.equals("unique"))
            to.setNumeric();
        else
            Fields.copyBaseProperties(to, src);
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

}
