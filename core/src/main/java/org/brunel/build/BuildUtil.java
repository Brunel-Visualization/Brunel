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

package org.brunel.build;

import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Field;
import org.brunel.data.util.DateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Utilities for writing D3 code
 */
public class BuildUtil {

    private static final boolean DEBUG = false;

    public static String writeCall(Field f) {
        return writeCall(f, false);
    }

    public static String writeCall(Field f, boolean dataInside) {
        return "data." + baseFieldID(f) + (dataInside ? "(d.data)":"(d)");
    }

    public static String baseFieldID(Field f) {
        return canonicalFieldName(f.name);
    }

    public static String canonicalFieldName(String s) {
        // The first character determines the type of field:
        // ' means it is a quoted constant ('abc' or '5.1232')
        // # means it is a special field (#count, #series)
        // Anything else and it is a regular field, known to be good
        char c = s.charAt(0);

        if (c == '#')
            return '$' + s.substring(1);                // #count -> $count
        if (c == '\'') {
            return s.replaceAll("['.+\\- ]", "_");          // transform numeric symbols and quotes
        }

        return s;                                       // As it is
    }

    public static void addTiming(String s, ScriptWriter out) {
        if (DEBUG) out.onNewLine().add("BrunelD3.time(" + out.quote(s) + ")").endStatement();
    }

    public static class DateBuilder {
        private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        public String make(Date d, DateFormat dateFormat, boolean wrappedWithMake) {
            calendar.setTime(d);
            String text;
            if (dateFormat.ordinal() >= DateFormat.YearMonthDay.ordinal()) {
                // YYYY-MM-DD
                text = String.format("'%d-%02d-%02d'",
                        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
            } else {
                text = String.format("'%d-%02d-%02dT%02d:%02d:%02d'",
                        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
                        calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND)
                );
            }
            return wrappedWithMake ? "new Date(" + text + ")" : text;
        }

    }
}
