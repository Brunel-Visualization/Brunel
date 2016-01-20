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

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.util.ItemsList;
import org.brunel.data.util.Range;

import java.util.ArrayList;
import java.util.List;

public final class SummaryValues {
    private final Field[] fields;                                    // the fields we use
    public final List<Integer> rows = new ArrayList<Integer>();    // Which data rows have been aggregated into this
    public double[] percentSums;

    public SummaryValues(Field[] fields) {
        this.fields = fields;
    }

    public int firstRow() {
        return rows.get(0);
    }

    /*
     * Possible summaries are:
     * [numeric] mean, min, max, range, iqr, median, stddev
     * [any] count, mode, unique
     */
    public Object get(int fieldIndex, MeasureField m, Field[] xFields) {
        String summary = m.measureFunction;
        if (summary.equals("count")) return rows.size();
        Field x = xFields.length == 0 ? null : xFields[xFields.length-1];   // Innermost is the one

        if (summary.equals("fit")) {
            if (m.fit == null) m.fit = new Regression(m.field, x);
            return m.fit.get(x.value(rows.get(0)));
        }

        if (summary.equals("smooth")) {
            Double windowPercent = null;
            if (m.option != null)
                windowPercent = Double.parseDouble(m.option);
            if (m.fit == null) m.fit = new Smooth(m.field, x, windowPercent);
            return m.fit.get(x.value(rows.get(0)));
        }

        Object[] data = new Object[rows.size()];
        for (int i = 0; i < data.length; i++)
            data[i] = fields[fieldIndex].value(rows.get(i));

        Field f = Data.makeColumnField("temp", null, data);

        Double mean = f.numericProperty("mean");
        if (summary.equals("percent")) {
            if (mean == null) return null;
            double sum = percentSums[fieldIndex];
            return sum > 0 ? 100 * mean * f.numericProperty("valid") / sum : null;
        }

        if (summary.equals("range")) {
            if (mean == null) return null;
            Double low = f.numericProperty("min");
            Double high = f.numericProperty("max");
            return low == null ? null : Range.make(low, high, m.getDateFormat());
        }
        if (summary.equals("iqr")) {
            if (mean == null) return null;
            Double low = f.numericProperty("q1");
            Double high = f.numericProperty("q3");
            return low == null ? null : Range.make(low, high, m.getDateFormat());
        }

        if (summary.equals("sum")) {
            if (mean == null) return null;
            return mean * f.numericProperty("valid");
        }
        if (summary.equals("list")) {
            ItemsList categories = new ItemsList((Object[]) f.property("categories"), m.getDateFormat());
            if (m.option != null) {
                int displayCount = Integer.parseInt(m.option);
                categories.setDisplayCount(displayCount);
            }
            return categories;
        }
        return f.property(summary);
    }

}
