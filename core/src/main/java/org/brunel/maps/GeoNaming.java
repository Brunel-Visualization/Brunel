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

package org.brunel.maps;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Works out names of places and compares them
 */
public class GeoNaming {

    private static final Pattern PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+"); // Removes diacretics

    private static final String MAPPING = "britain:united kingdom|great britain:united kingdom" +
            "|united states:united states of america|usa:united states of america" +
            "|burma:myanmar|vatican city:vatican";

    private static final Map<String, String> commonNames = new HashMap<String, String>();

    static {
        for (String s : MAPPING.split("\\|")) {
            String[] p = s.split(":");
            commonNames.put(p[0], p[1]);
        }
    }

    public static String canonical(String s) {
        s = expandAbbreviations(s.toLowerCase());
        s = s.replaceAll("[ \t]+", " ");
        String common = commonNames.get(s);
        if (common != null) s = common;
        // Lots of variations of this start
        if (s.startsWith("united kingdom of")) return "united kingdom";
        if (s.startsWith("holy see")) return "vatican";
        return s;
    }

    public static String expandAbbreviations(String s) {
        return s.replaceAll("st\\.[ ]*", "saint ")
                .replaceAll("dem\\.[ ]*", "democratic ")
                .replaceAll("rep\\.[ ]*", "republic ")
                .replaceAll("is\\.[ ]*", "islands ")
                .replaceAll("\u2019", "'")
                .replaceAll("&", " and ")
                .replaceAll(" [ ]+", " ").trim();
    }

    static String removeAccents(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return PATTERN.matcher(decomposed).replaceAll("");
    }

    static String removePeriods(String s) {
        // Do not remove from XX.YY pattern
        if (s.length() == 5 && s.charAt(2) == '.') return s;
        return s.replaceAll("\\.", "");
    }

    public static List<String> variants(String name) {
        List<String> variants = new ArrayList<String>();

        String t = removeAccents(name);
        if (!name.equals(t)) variants.add(t);

        t = removePeriods(name);
        if (!name.equals(t)) variants.add(t);

        int p = name.indexOf(',');
        if (p > 0) {
            // Instead of  "Netherlands, The", try "The Netherlands"
            variants.add(name.substring(p + 1).trim() + " " + name.substring(0, p));
            // Instead of  "Netherlands, The", try "Netherlands"
            variants.add(name.substring(0, p));
        }

        p = name.indexOf('(');
        if (p > 0) {
            // Things like "Myanmar(burma)"
            variants.add(name.substring(0, p).trim());
        }

        return variants;
    }

}
