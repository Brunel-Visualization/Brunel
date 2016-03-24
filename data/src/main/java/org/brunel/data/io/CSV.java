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

package org.brunel.data.io;

import org.brunel.data.Field;
import org.brunel.data.Fields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSV {

    /*
     * Reads CSV formatted data and converts to values
     * It assumes the data has headers
     */
    public static Field[] read(String base) {
        String[][] data = parse(base);
        return makeFields(data);
    }

    /*
     * Reads CSV formatted data and converts to array of values
     */
    public static String[][] parse(String data) {
        Map<String, String> common = new HashMap<>();
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        char last = ' ';
        boolean inQuote = false, wasQuoted = false;
        int currentIndex = 0;

        char separator = findSeparator(data);
        String building = null;

        int fieldCount = -1;
        while (currentIndex <= data.length()) {
            // Ensure the last character in the file is an additional return
            char c = currentIndex == data.length() ? '\n' : data.charAt(currentIndex);
            if (inQuote) {
                if (c == '\"') {
                    if (currentIndex < data.length() - 1 && data.charAt(currentIndex + 1) == '\"') {
                        // A double quote is treated as a single quote
                        building += '\"';
                        currentIndex++;
                    } else
                        inQuote = false;
                } else
                    // Simply add whatever it is
                    building += c;
            } else if (c == '\n' || c == '\r') {
                // Ignore the second of a \n\r
                if (last != '\r' || c != '\n') {
                    if (line.isEmpty() && (building == null || building.trim().length() == 0)) {
                        // An empty line means the end of parsing
                        break;
                    }
                    // Add to the line and add the line to the list of lines
                    line.add(saveMemory(building, common, wasQuoted));
                    lines.add(line);
                    if (fieldCount < 0)
                        fieldCount = line.size();
                    else if (fieldCount != line.size())
                        throw new IllegalArgumentException("Line " + lines.size() + " had " + line.size() + " entries; expected "
                                + fieldCount);
                    line = new ArrayList<>();
                    building = null;
                    wasQuoted = false;
                }
            } else if (c == '\"') {
                inQuote = true;
                wasQuoted = true;
                if (building == null) building = "";
            } else {
                if (c == separator) {
                    line.add(saveMemory(building, common, wasQuoted));
                    building = null;
                    wasQuoted = false;
                } else {
                    if (building == null)
                        building = "";
                    building += c;
                }
            }
            last = c;
            currentIndex++;
        }

        String[][] result = new String[lines.size()][];
        for (int i = 0; i < result.length; i++) {
            List<String> row = lines.get(i);
            result[i] = row.toArray(new String[row.size()]);
        }
        return result;

    }

    private static char findSeparator(String data) {
        char[] potential = new char[]{',', '\t', '|', ';'};
        char best = ',';
        int score = -100000;

        // Do not look for more lines than this
        int N = Math.min(5000, data.length());

        for (int i = 0; i < 4; i++) {
            char trial = potential[i];
            // Step through two lines and find how many on each line
            int[] count = new int[]{0, 0};
            int line = 0;
            for (int j = 0; j < N && line < 2; j++) {
                char s = data.charAt(j);
                if (s == trial) count[line]++;
                if (s == '\n') line++;
            }
            // Score matches well, everything else less so
            int trialScore = count[0];
            if (count[0] != count[1] || count[0] == 0) trialScore -= 10000;
            if (trialScore > score) {
                best = trial;
                score = trialScore;
            }
        }
        return best;
    }

    private static String saveMemory(String s, Map<String, String> common, boolean wasQuoted) {
        if (s == null) return null;
        if (!wasQuoted) s = s.trim();

        // Save memory by re-using common strings
        String t = common.get(s);
        if (t == null) {
            common.put(s, s);
            return s;
        } else {
            return t;
        }
    }

    /*
     * Reads CSV formatted data and converts to values
     * It assumes the data has headers
     */
    public static Field[] makeFields(Object[][] data) {
        Field[] fields = new Field[data[0].length];
        for (int i = 0; i < fields.length; i++) {
            Object[] column = new Object[data.length - 1];
            for (int j = 0; j < column.length; j++)
                column[j] = data[j + 1][i];
            String name = data[0][i] == null ? "" : data[0][i].toString();
            fields[i] = Fields.makeColumnField(identifier(name), readable(name), column);
        }
        return fields;
    }

    public static String identifier(String text) {
        int parenthesis = text.indexOf('(');
        if (parenthesis > 0) text = text.substring(0, parenthesis).trim();

        String result = "";
        String last = "X";
        for (int i = 0; i < text.length(); i++) {
            String c = text.substring(i, i + 1);
            String d;
            if (isDigit(c)) {
                if (i == 0) result = "_";                               // Digits need a lead underscore to be legal
                d = c;
            } else if (c.equals("_") || isLower(c) || isUpper(c)) {
                if (result.equals("_")) result = "";                    // No need for the lead underscore
                d = c;
            } else {
                d = "_";
            }
            if (d.equals("_")) {
                if (!last.equals("_")) result += d;
            } else {
                result += d;
            }
            last = d;
        }
        return result.length() == 0 ? "_" : result;
    }

    public static String readable(String text) {
        String built = "";                                  // Assemble this string
        String last = " ";                                  // Last character processed
        boolean lastLower = false;                          // Case of last character
        for (int i = 0; i < text.length(); i++) {
            String s = text.substring(i, i + 1);
            if (s.equals("_")) s = " ";                     // underscores are spaces
            boolean lower = isLower(s);
            boolean upper = isUpper(s);
            if (s.equals(" ")) {
                if (!last.equals(" ")) built += s;
            } else if (lower) {
                // After a space, capitalize lowercase
                if (last.equals(" ")) built += s.toUpperCase();
                else built += s;
            } else {
                // Add a space between a lower case and an uppercase or digit
                if (lastLower && (upper || isDigit(s))) built += " " + s;
                else built += s;
            }
            lastLower = lower;
            last = s;
        }
        return built;
    }

    private static boolean isDigit(String c) {
        return "0123456789".indexOf(c) >= 0;
    }

    private static boolean isLower(String c) {
        return "abcdefghijklmnopqrstuvwxyz".indexOf(c) >= 0;
    }

    private static boolean isUpper(String c) {
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(c) >= 0;
    }

}
