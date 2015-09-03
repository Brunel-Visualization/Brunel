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

package org.brunel.build.d3;

import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Field;
import org.brunel.data.util.DateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utilities for writing D3 code
 */
public class D3Util {

    private static final boolean DEBUG = false;

    public static String baseFieldID(Field f) {
        return canonicalFieldName(f.name);
    }

    public static double asPixels(String text, double def) {
        if (text == null) return def;
        if (text.endsWith("px")) text = text.substring(0, text.length() - 2);
        return Double.parseDouble(text);
    }

    public static String canonicalFieldName(String s) {
        // The first character determines the type of field:
        // ' means it is a quoted constant ('abc' or '5.1232')
        // # means it is a special field (#count, #series)
        // Anything else and it is a regular field, known to be good
        char c = s.charAt(0);

        if (c=='#')
            return '$' + s.substring(1);                // #count -> $count
        if (c =='\'') {
            s = s.substring(1, s.length()-1)            // remove the quotes
                .replaceAll("[.+\\-]", "_");            // transform numeric symbols
            return '_' + s;                             // Prepend '_'
        }

        return s;                                       // As it is
    }

    public static String writeCall(Field f) {
        return "data." + baseFieldID(f) + "(d)";
    }

    public static void addTiming(String s, ScriptWriter out) {
        if (DEBUG) out.onNewLine().add("BrunelD3.time(" + out.quote(s) + ")").endStatement();
    }

    public static class DateBuilder {
        private final Calendar calendar = Calendar.getInstance(Locale.US);

        public String make(Date d, DateFormat dateFormat) {
            calendar.setTime(d);
            String parts = calendar.get(Calendar.YEAR) + "," + calendar.get(Calendar.MONTH) + "," + calendar.get(Calendar.DAY_OF_MONTH);
            if (dateFormat.ordinal() < DateFormat.YearMonthDay.ordinal())
                parts += "," + calendar.get(Calendar.HOUR) + "," + calendar.get(Calendar.MINUTE) + "," + calendar.get(Calendar.SECOND);
            return "new Date(" + parts + ")";
        }

    }
}
