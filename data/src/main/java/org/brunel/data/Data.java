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
import org.brunel.data.util.ItemsList;
import org.brunel.data.util.Range;
import org.brunel.translator.JSTranslation;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            "if (typeof(o) == 'number') return $$CLASS$$.formatNumeric(o, null, useGrouping);",
            "return '' + o;"
    })
    public static String format(Object o, boolean useGrouping) {
        if (o == null) return "?";
        if (o instanceof Number) return formatNumeric(((Number) o).doubleValue(), null, useGrouping);
        return o.toString();
    }

    @JSTranslation(js = {
            "if (d == 0) return '0';",
            "if (Math.abs(d) <= 1e-6 || Math.abs(d) >= 1e8) return $.formatScientific(d);",
            "if (Math.abs((d - Math.round(d)) / d) < 1e-9) return $.formatInt(Math.round(d), useGrouping);",
            "return $.formatFixed(d, decimalPlaces == null ? 6 : decimalPlaces, useGrouping);"
    })
    public static String formatNumeric(double d, Number decimalPlaces, boolean useGrouping) {
        if (d == 0) return "0";
        if (Math.abs(d) <= 1e-6 || Math.abs(d) >= 1e8)
            return scientificFormat.format(d).replace('E', 'e');
        else if (Math.abs((d - Math.round(d)) / d) < 1e-9)
            return Math.abs(d) >= 1e4 && useGrouping ? bigIntegerFormat.format(d) : integerFormat.format(d);
        else {

            int place = (decimalPlaces != null) ? decimalPlaces.intValue() :
                    7 - Math.min(7, Math.max(0, (int) Math.floor(Math.log10(d))));
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

    @JSTranslation(js = "$.sort(data, $$CLASS$$.compare)")
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
                if (v != null && v > 0) data[i] = asDate(Data.format(v, false) + "-01-01");
            } else if ("excel".equals(method)) {
                // Must be numeric and is equal to the number of days since 1900.
                // We use the number of days since 1970, so we need to subtract the difference
                Double v = asNumeric(o);
                if (v != null) data[i] = asDate(v - 24107);
            } else
                data[i] = asDate(o);
            if (!changed) changed = Data.compare(o, data[i]) != 0;
        }
        Field result = Fields.makeColumnField(f.name, f.label, data);
        result.set("date", true);
        result.setNumeric();
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

    public static Field toNumeric(Field f) {
        if (f.isNumeric()) return f;
        Number[] data = new Number[f.rowCount()];
        for (int i = 0; i < data.length; i++) {
            Object o = f.value(i);
            data[i] = asNumeric(o);
        }
        Field result = Fields.makeColumnField(f.name, f.label, data);
        result.setNumeric();
        return result;
    }

    public static Field toList(Field base) {
        // Find the best separator -- the one occurring in most lines
        char sep = ',';
        int nSep = -1;
        for (char s : new char[]{',', ';', '|'}) {
            int c = 0;
            for (Object o : base.categories())
                if (o.toString().indexOf(c) >= 0) c++;
            if (c > nSep) {
                sep = s;
                nSep = c;
            }
        }

        int n = base.rowCount();

        // Maps to common list items; used to get the list categories and
        // also to pool strings
        Map<String, String> commonParts = new HashMap<>();

        // Create items lists and accumulate general info
        ItemsList[] items = new ItemsList[n];
        for (int i = 0; i < n; i++) {
            Object o = base.value(i);
            if (o == null) continue;

            // These are the valid items in the list
            List<String> valid = new ArrayList<>();

            for (String s : Data.split(o.toString(), sep)) {
                s = s.trim();
                if (s.length() > 0) {
                    String common = commonParts.get(s);
                    if (common == null) {
                        common = s;
                        commonParts.put(common, s);
                    }
                    valid.add(common);
                }
            }
            items[i] = new ItemsList(valid.toArray(new String[valid.size()]));
        }

        // Passed the tests so return the details!
        Field f = Fields.makeColumnField(base.name, base.label, items);
        Collection<String> parts = commonParts.values();
        String[] common = parts.toArray(new String[parts.size()]);
        Arrays.sort(common);
        f.set("list", true);
        f.set("listCategories", common);
        return f;
    }

    // Javascript does not use regexes for split; Java does
    @JSTranslation(js = "return text.split(sep);")
    public static String[] split(String text, char sep) {
        return text.split("\\" + sep);
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
        if (s == null) return "null";
        // get most appropriate quote character
        char quoteChar = '\'';
        if (s.indexOf(quoteChar) >= 0) quoteChar = '"';

        String text = "";
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\n') text += "\\n";
            if (c == '\r') text += " ";
            else if (c == '\t') text += "\\t";
            else if (c == '\\') text += "\\\\";
            else if (c == quoteChar) text += "\\" + c;
            else if (c >= '\u00ff') text += "\\u" + hex4Format(c);          // escape non-unicode
            else text += c;
        }

        return quoteChar + text + quoteChar;
    }

    @JSTranslation(js = "return ('0000' + c.charCodeAt(0).toString(16)).substr(-4);")
    private static String hex4Format(char c) {
        return String.format("%04X", (int) c);

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

    @JSTranslation(js = "return items;")
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
