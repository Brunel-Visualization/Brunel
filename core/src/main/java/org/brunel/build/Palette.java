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

package org.brunel.build;

import org.brunel.data.Field;

import java.util.HashMap;
import java.util.Map;

public class Palette {

	/* BA1 Style Guide 0.9 WIP */

    private static final String[] NOMINAL = new String[]{"#319BD4", "#86C43B", "#F5CE22", "#E86A3F", "#9380E8", "#4E3287",
            "#14C59E", "#24A399", "#14665E", "#B4C8ED", "#77A3E0", "#396CB3", "#1D4273", "#F2BFD4", "#E086B6", "#B33681", "#73154D"};

    private static final String[] CONTINUOUS = new String[]{"#E2FCFD", "#BEF5F3", "#91E3E8", "#6CCAE0", "#00A7D8", "#006899", "#00558B", "#00386A", "#001F44"};

    private static final String[] DIVERGING = new String[]{"#003159", "#006198", "#549CD0", "#A7D2F4", "#F7F4DD",
            "#FFC9A1", "#ED824B", "#BC4501", "#752200"};

    private static final Map<String, String[]> palettes = new HashMap<String, String[]>();

    public static String[] makePalette(Field f, String hint) {

        if (hint != null) {
            String[] palette = palettes.get(hint.toLowerCase());
            if (palette != null) return palette;
        }

        int n = f.getNumericProperty("unique").intValue();

        // Ordinal first
        boolean ordinal = "ordinal".equals(hint) || f.preferCategorical() && f.hasProperty("numeric");
        if (ordinal) return ordinalPalette(n);

        // If categorical or binary, use the nominal palette
        if (n < 3 || f.preferCategorical()) return NOMINAL;

        /// Default to divergent, except for given cases below
        boolean divergent = true;
        if (f.name.startsWith("#")) divergent = false;
        String summary = f.getStringProperty("summary");
        if ("sum".equals(summary) || "percent".equals(summary)) divergent = false;

        if (divergent) return DIVERGING;
        else return CONTINUOUS;
    }

    private static String hexInterpolate(String a, String b, int at, double t) {
        double v1 = Integer.parseInt(a.substring(at, at + 2), 16);
        double v2 = Integer.parseInt(b.substring(at, at + 2), 16);
        int v = (int) Math.round(v1 * (1 - t) + v2 * t);
        return v < 16 ? "0" + Integer.toHexString(v) : Integer.toHexString(v);
    }

    private static String interpolate(String[] palette, double v) {
        String a = palette[(int) Math.floor(v)];
        String b = palette[(int) Math.ceil(v)];
        double t = v - Math.floor(v);
        return "#" + hexInterpolate(a, b, 1, t) + hexInterpolate(a, b, 3, t) + hexInterpolate(a, b, 5, t);
    }

    private static String[] ordinalPalette(int n) {
        String[] subset = subset(n, CONTINUOUS);
        if (subset != null) return subset;
        subset = ordinalPalette(9);
        String[] result = new String[n];
        for (int i = 0; i < n; i++)
            result[i] = interpolate(subset, i * 8.0 / (n - 1));
        return result;
    }

    private static String[] reverse(String[] s) {
        String[] r = new String[s.length];
        for (int i = 0; i < s.length; i++) r[i] = s[s.length - 1 - i];
        return r;
    }

    private static String[] subset(int n, String[] p) {
        if (n == 2) return new String[]{p[0], p[8]};
        if (n == 3) return new String[]{p[0], p[4], p[8]};
        if (n == 4) return new String[]{p[0], p[3], p[5], p[8]};
        if (n == 5) return new String[]{p[0], p[2], p[4], p[6], p[8]};
        if (n == 6) return new String[]{p[0], p[2], p[3], p[5], p[6], p[8]};
        if (n == 7) return new String[]{p[0], p[2], p[3], p[4], p[5], p[6], p[8]};
        if (n == 8) return new String[]{p[0], p[1], p[2], p[3], p[5], p[6], p[7], p[8]};
        if (n == 9) return p;
        return null;
    }

    static {
        palettes.put("nominal", NOMINAL);
        palettes.put("continuous", CONTINUOUS);
        palettes.put("diverging", DIVERGING);
        palettes.put("blue", CONTINUOUS);
        palettes.put("green", "#F6FAF3 #DFF0D9 #AFE0A5 #8DC945 #6AAA1F #498313 #17631E #004527 #012D11".split(" "));
        palettes.put("red", "#FFF6EB #FEE5DC #F8C2B0 #F78261 #E2423F #B61813 #840912 #56051E #390213".split(" "));
        palettes.put("yellow", "#fcecb9 #efd873 #fcd21d #e5b900 #c19c00 #967900 #635000 #3f3300 #271e00".split(" "));
        palettes.put("gray", "#ffffff #e0e0e0 #c0c0c0 #a0a0a0 #808080 #606060 #404040 #202020 #000000".split(" "));
        palettes.put("blue-red", DIVERGING);
        palettes.put("green-red", "005029 #4D8900 #7FCA27 #BAE77F #F1F3DC #F9C6C9 #EC6A70 #A92329 #610205".split(" "));
        palettes.put("green-blue", "005029 #4D8900 #7FCA27 #BAE77F #DBF3EC #A7D2F4 #549CD0 #006198 #003159".split(" "));
        palettes.put("white-black", palettes.get("gray"));
        palettes.put("black-white", reverse(palettes.get("gray")));
        palettes.put("red-blue", reverse(palettes.get("blue-red")));
        palettes.put("red-green", reverse(palettes.get("green-red")));
        palettes.put("blue-green", reverse(palettes.get("green-blue")));

    }
}
