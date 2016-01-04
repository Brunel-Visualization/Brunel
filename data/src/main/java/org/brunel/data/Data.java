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

package org.brunel.data;

import org.brunel.data.util.Dates;
import org.brunel.data.util.Range;
import org.brunel.data.values.ColumnProvider;
import org.brunel.data.values.ConstantProvider;
import org.brunel.data.values.ReorderedProvider;
import org.brunel.data.values.RowProvider;
import org.brunel.translator.JSTranslation;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;

public class Data {

    @JSTranslation(ignore = true)
    public static final double MILLIS_PER_DAY = 86400000.0;
    @JSTranslation(ignore = true)
    private static final DecimalFormat bigIntegerFormat = new DecimalFormat("0,000");
    @JSTranslation(ignore = true)
    private static final DecimalFormat integerFormat = new DecimalFormat("0");
    @JSTranslation(ignore = true)
    private static final DecimalFormat[] numericFormatGrouped = new DecimalFormat[]{
            new DecimalFormat("#,###"),
            new DecimalFormat("#,###.#"),
            new DecimalFormat("#,###.##"),
            new DecimalFormat("#,###.###"),
            new DecimalFormat("#,###.####"),
            new DecimalFormat("#,###.#####"),
            new DecimalFormat("#,###.######"),
            new DecimalFormat("#,###.#######"),
    };
    @JSTranslation(ignore = true)
    private static final DecimalFormat[] numericFormat = new DecimalFormat[]{
            new DecimalFormat("#"),
            new DecimalFormat("#.#"),
            new DecimalFormat("#.##"),
            new DecimalFormat("#.###"),
            new DecimalFormat("#.####"),
            new DecimalFormat("#.#####"),
            new DecimalFormat("#.######"),
            new DecimalFormat("#.#######"),
    };
    @JSTranslation(ignore = true)
    private static final DecimalFormat scientificFormat = new DecimalFormat("0.0##E0");

    public static int indexOf(double v, Double[] d) {
        // Find the index of the highest value less than or equal to 'v' in the sorted array 'd'
        // We use binary search and return -1 if the lowest value is still too high
        int low = -1;
        int high = d.length;
        while (high - low > 1) {
            int mid = (int) ((high + low) / 2.0);   // Explicit casts needed for Javascript ("2.0" makes inspections ok)
            if (d[mid] <= v)
                low = mid;
            else
                high = mid;
        }
        return low;
    }

    @JSTranslation(ignore = true)
    public static String join(Collection<?> items) {
        return join(items, null);
    }

    public static String join(Collection<?> items, String inter) {
        if (inter == null) inter = ", ";
        String s = "";
        boolean first = true;
        for (Object o : items) {
            if (!first) s += inter;
            s += Data.format(o, false);
            first = false;
        }
        return s;
    }

    @JSTranslation(js = {
            "if (o == null) return '?';",
            "if (typeof(o) == 'number') return $$CLASS$$.formatNumeric(o, useGrouping);",
            "return '' + o;"
    })
    public static String format(Object o, boolean useGrouping) {
        if (o == null) return "?";
        if (o instanceof Number) return formatNumeric(((Number) o).doubleValue(), useGrouping);
        return o.toString();
    }

    @JSTranslation(js = {
            "if (d == 0) return '0';",
            "if (Math.abs(d) <= 1e-6 || Math.abs(d) >= 1e8) return $.formatScientific(d);",
            "if (Math.abs((d - Math.round(d)) / d) < 1e-9) return $.formatInt(Math.round(d), useGrouping);",
            "return $.formatFixed(d, useGrouping);"
    })
    public static String formatNumeric(double d, boolean useGrouping) {
        if (d == 0) return "0";
        if (Math.abs(d) <= 1e-6 || Math.abs(d) >= 1e8)
            return scientificFormat.format(d).replace('E', 'e');
        else if (Math.abs((d - Math.round(d)) / d) < 1e-9)
            return Math.abs(d) >= 1e4 && useGrouping ? bigIntegerFormat.format(d) : integerFormat.format(d);
        else {
            int place = 7 - Math.min(7, Math.max(0, (int) Math.floor(Math.log10(d))));
            return (useGrouping ? numericFormatGrouped : numericFormat)[place].format(d);
        }
    }

    @JSTranslation(ignore = true)
    public static String join(double[] items) {
        Double[] oo = new Double[items.length];
        for (int i = 0; i < oo.length; i++) oo[i] = items[i];
        return join(oo, null);
    }

    @JSTranslation(ignore = true)
    public static String join(int[] items) {
        Integer[] oo = new Integer[items.length];
        for (int i = 0; i < oo.length; i++) oo[i] = items[i];
        return join(oo, null);
    }

    @JSTranslation(ignore = true)
    // The Javascript version works fine with the Collections version
    public static String join(Object[] items, String inter) {
        if (inter == null) inter = ", ";
        String s = "";
        for (int i = 0; i < items.length; i++) {
            if (i > 0) s += inter;
            s += Data.format(items[i], false);
        }
        return s;
    }

    @JSTranslation(ignore = true)
    public static String join(Object[] items) {
        return join(items, null);
    }

    /* Makes a field that is a constant value */
    public static Field makeConstantField(String name, String label, Object o, int len) {
        Field field = new Field(name, label, new ConstantProvider(o, len));
        if (Data.asNumeric(o) != null) field.set("numeric", true);
        return field;
    }

    @JSTranslation(js = {
            "if (c == null) return null;",
            "if (c && c.asNumeric) return c.asNumeric();",
            "if (c && c.getTime) return c.getTime() / 86400000;",
            "var v = Number(c); ",
            "if (!isNaN(v)) return v;",
            "return null;"
    })
    public static Double asNumeric(Object c) {
        if (c == null) return null;
        Double d = null;
        if (c instanceof Double)
            d = (Double) c;
        if (c instanceof Number)
            d = ((Number) c).doubleValue();
        if (c instanceof Range)
            return ((Range) c).asNumeric();
        if (c instanceof Date)
            return ((Date) c).getTime() / MILLIS_PER_DAY;
        else if (c instanceof String) try {
            String s = ((String) c).trim();
            if (s.isEmpty()) return null;
            d = Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            // We do not convert string -> date -> numbers; that must be done manually
        }

        return d == null || Double.isNaN(d) ? null : d;
    }

    /* Makes a field that indexes the data */
    public static Field makeIndexingField(String name, String label, int len) {
        Field field = new Field(name, label, new RowProvider(len));
        field.set("numeric", true);
        return field;
    }

    @JSTranslation(js = {"$.sort(data, $$CLASS$$.compare)"})
    public static void sort(Object[] data) {
        Arrays.sort(data, new Comparator<Object>() {

            public int compare(Object o1, Object o2) {
                return Data.compare(o1, o2);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @JSTranslation(js = "return $.compare(a,b);")
    public static int compare(Object a, Object b) {
        // Sort nulls to end
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        if (a.getClass() != b.getClass())
            return a.getClass().getCanonicalName().compareTo(b.getClass().getCanonicalName());
        return ((Comparable) a).compareTo(b);
    }

    @JSTranslation(ignore = true)
    public static Field toDate(Field f) {
        return toDate(f, null);
    }

    public static Field toDate(Field f, String method) {
        if (f.isDate()) return f;
        Date[] data = new Date[f.rowCount()];
        boolean changed = false;
        for (int i = 0; i < data.length; i++) {
            Object o = f.value(i);
            if ("year".equals(method)) {
                // Must be numeric and is equal to years
                Double v = asNumeric(o);
                if (v != null) data[i] = asDate(Data.format(v, false) + "-01-01");
            } else if ("excel".equals(method)) {
                // Must be numeric and is equal to the number of days since 1900.
                // We use the number of days since 1970, so we need to subtract the difference
                Double v = asNumeric(o);
                if (v != null) data[i] = asDate(v - 24107);
            } else
                data[i] = asDate(o);
            if (!changed) changed = Data.compare(o, data[i]) != 0;
        }
        Field result = makeColumnField(f.name, f.label, data);
        result.set("date", true);
        result.set("numeric", true);
        return result;
    }

    // JavaScript date parsing uses LOCAL time zone for non ISO-8601 dates,
    // But UTC for ISO-6801 dates, so we need to undo that by detecting the ISO format 'T'
    @JSTranslation(js = {
            "if (c==null) return null;",
            "if (c.getTime) return c;",
            "if (typeof c == 'string') {d = $.parseDate(c); return d == null || isNaN(d.getTime()) ? null : d };",
            "if (!isNaN(c)) return new Date(c*86400000);",
            "return null;"
    })
    public static Date asDate(Object c) {
        return Dates.parse(c);
    }

    /* Makes a field from a column of data */
    public static Field makeColumnField(String name, String label, Object[] data) {
        return new Field(name, label, new ColumnProvider(data));
    }

    public static Field makeIndexedColumnField(String name, String label, Object[] items, int[] indices) {
        return new Field(name, label, new ReorderedProvider(new ColumnProvider(items), indices));
    }

    public static Field toNumeric(Field f) {
        if (f.isNumeric()) return f;
        boolean changed = false;
        Number[] data = new Number[f.rowCount()];
        for (int i = 0; i < data.length; i++) {
            Object o = f.value(i);
            data[i] = asNumeric(o);
            if (!changed) changed = Data.compare(o, data[i]) != 0;
        }
        Field result = changed ? makeColumnField(f.name, f.label, data) : f;
        result.set("numeric", true);
        return result;
    }

    /**
     * Modify the data for the field
     *
     * @param field            field to permute
     * @param order            the new order
     * @param onlyOrderChanged true if this is a true permutation only
     * @return new field
     */
    public static Field permute(Field field, int[] order, boolean onlyOrderChanged) {
        if (onlyOrderChanged)
            return new Field(field.name, field.label, new ReorderedProvider(field.provider, order), field);

        Field f = new Field(field.name, field.label, new ReorderedProvider(field.provider, order));
        Data.copyBaseProperties(f, field);
        return f;
    }

    public static void copyBaseProperties(Field target, Field source) {
        target.set("numeric", source.property("numeric"));
        target.set("binned", source.property("binned"));
        target.set("summary", source.property("summary"));
        target.set("transform", source.property("transform"));
        if (source.propertyTrue("categoriesOrdered")) {
            target.set("categoriesOrdered", true);
            target.set("categories", source.property("categories"));
        }
        if (source.isDate()) {
            target.set("date", true);
            target.set("dateUnit", source.property("dateUnit"));
            target.set("dateFormat", source.property("dateFormat"));
        }
    }

    public static boolean isQuoted(String txt) {
        if (txt == null || txt.length() < 2) return false;
        char c = txt.charAt(0);
        return c == '"' || c == '\'' && txt.charAt(txt.length() - 1) == c;
    }

    public static String deQuote(String s) {
        String text = "";
        int n = s.length() - 1;
        for (int i = 1; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                c = s.charAt(++i);
                if (c == 'n') text += "\n";
                else if (c == 't') text += "\t";
                else text += c;
            } else {
                text += c;
            }
        }
        return text;
    }

    public static String quote(String s) {
        // get most appropriate quote character
        char quoteChar = '\'';
        if (s.indexOf(quoteChar) >= 0) quoteChar = '"';

        String text = "";
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\n') text += "\\n";
            else if (c == '\t') text += "\\t";
            else if (c == '\\') text += "\\\\";
            else if (c == quoteChar) text += "\\" + c;
            else text += c;
        }

        return quoteChar + text + quoteChar;
    }

    @JSTranslation(ignore = true)
    public static Integer[] order(int[] c, boolean ascending) {
        // In java only, need to convert types
        Integer[] cc = new Integer[c.length];
        for (int i = 0; i < cc.length; i++) cc[i] = c[i];
        return order(cc, ascending);
    }

    @JSTranslation(js = {
            "var v = [];",
            "for (var i=0; i<c.length; i++) v.push(i);",
            "v.sort(function(s,t) { var r= $.compare(c[s], c[t]); return ascending ? r : -r});",
            "return v;"
    })
    public static Integer[] order(Object[] c, boolean ascending) {
        Integer[] o = new Integer[c.length];
        for (int i = 0; i < o.length; i++) o[i] = i;
        Arrays.sort(o, new IndexedCompare(c, ascending));
        return o;
    }

    @JSTranslation(js = {"return items;"})
    public static int[] toPrimitive(Integer[] items) {
        int[] result = new int[items.length];
        for (int i = 0; i < items.length; i++) result[i] = items[i];
        return result;
    }

    /**
     * Compares integers by their indexed value
     */
    @JSTranslation(ignore = true)
    static class IndexedCompare implements Comparator<Integer> {
        private final Object[] base;
        private final boolean ascending;

        public IndexedCompare(Object[] base, boolean ascending) {
            this.base = base;
            this.ascending = ascending;
        }

        public int compare(Integer o1, Integer o2) {
            int v = Data.compare(base[o1], base[o2]);
            return ascending ? v : -v;
        }
    }
}
