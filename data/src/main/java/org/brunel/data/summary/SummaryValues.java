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

package org.brunel.data.summary;

import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.util.ItemsList;
import org.brunel.data.util.Range;

import java.util.ArrayList;
import java.util.List;

public final class SummaryValues {
    private final Field[] fields;                                   // the fields we use
    private final Field[] xFields;                                  // the fields to use as 'X' values
    public final List<Integer> rows = new ArrayList<Integer>();     // Which data rows have been aggregated into this
    private final ArrayList<Field> groupFields;                     // Fields that group results
    public double[] percentSums;

    public SummaryValues(Field[] fields, Field[] xFields, Field[] allDimensions) {
        this.fields = fields;
        this.xFields = xFields;

        // Create an array of fields that group the results
        this.groupFields = new ArrayList<Field>();
        for (Field f : allDimensions) {
            boolean isGroup = true;
            for (Field x : xFields) if (x == f) isGroup = false;
            if (isGroup) groupFields.add(f);
        }
    }

    public int firstRow() {
        return rows.get(0);
    }

    /**
     * Calculate the summary value
     *
     * @param fieldIndex the index of the output field within this.fields
     * @param m          the measure we wish to calculate
     * @return the summary value
     */
    public Object get(int fieldIndex, MeasureField m) {
        String summary = m.method;
        if (summary.equals("count")) return rows.size();
        Field x = xFields.length == 0 ? null : xFields[xFields.length - 1];   // Innermost is the one
        int index = rows.get(0);

        if (summary.equals("fit")) {
            Fit fit = m.getFit(groupFields, index);
            if (fit == null) fit = new Regression(m.field, x, validForGroup(index));
            m.setFit(groupFields, index, fit);
            return fit.get(x.value(index));
        }

        if (summary.equals("smooth")) {
            Fit fit = m.getFit(groupFields, index);
            if (fit == null) {
                Double windowPercent = null;
                if (m.option != null)
                    windowPercent = Double.parseDouble(m.option);
                fit = new Smooth(m.field, x, windowPercent, validForGroup(index));
            }
            m.setFit(groupFields, index, fit);
            return fit.get(x.value(rows.get(0)));
        }

        Object[] data = new Object[rows.size()];
        for (int i = 0; i < data.length; i++)
            data[i] = fields[fieldIndex].value(rows.get(i));

        Field f = Fields.makeColumnField("temp", null, data);

        Double mean = f.numProperty("mean");
        if (summary.equals("percent")) {
            if (mean == null) return null;
            double sum;
            if ("overall".equals(m.option))
                sum = m.field.valid() * m.field.numProperty("mean");
            else
                sum = percentSums[fieldIndex];
            return sum > 0 ? 100 * mean * f.valid() / sum : null;
        }

        if (summary.equals("range")) return makeRange(m, f, "min", "max");

        if (summary.equals("iqr")) return makeRange(m, f, "q1", "q3");

        if (summary.equals("sum")) {
            if (mean == null) return null;
            return mean * f.numProperty("valid");
        }
        if (summary.equals("list")) {
            ItemsList categories = new ItemsList((Object[]) f.property("categories"));
            if (m.option != null) {
                int displayCount = Integer.parseInt(m.option);
                categories.setDisplayCount(displayCount);
            }
            return categories;
        }
        return f.property(summary);
    }

    protected Object makeRange(MeasureField m, Field f, String a, String b) {
        return Range.make(f.numProperty(a), f.numProperty(b), m.getDateFormat());
    }

    private List<Integer> validForGroup(int index) {
        ArrayList<Integer> list = new ArrayList<Integer>();
        int n = fields[0].rowCount();
        for (int i = 0; i < n; i++) {
            boolean valid = true;
            for (Field f : groupFields) if (f.compareRows(index, i) != 0) valid = false;
            if (valid) list.add(i);
        }
        return list;
    }

}
