package org.brunel.color;

import org.brunel.action.Param;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Palettes of color
 */
public class Palette {

    private static final Palette[] sequential = new Palette[]{
            new Palette("Blue;blue,purple;#f1eef6,#bdc9e1,#74a9cf,#2b8cbe,#045a8d"),
            new Palette("Green;blue,green;#edf8fb,#b2e2e2,#66c2a4,#2ca25f,#006d2c"),
            new Palette("PurpleBlue;blue,purple;#edf8fb,#b3cde3,#8c96c6,#8856a7,#810f7c"),
            new Palette("BlueGreen;green,blue;#f0f9e8,#bae4bc,#7bccc4,#43a2ca,#0868ac"),
            new Palette("Red;orange,red;#fef0d9,#fdcc8a,#fc8d59,#e34a33,#b30000"),
            new Palette("Purple;red,purple;#feebe2,#fbb4b9,#f768a1,#c51b8a,#7a0177"),
            new Palette("GreenYellow;green;#ffffcc,#c2e699,#78c679,#31a354,#006837"),
            new Palette("BlueYellow;green,blue;#ffffcc,#a1dab4,#41b6c4,#2c7fb8,#253494"),
            new Palette("Brown;orange,brown;#ffffd4,#fed98e,#fe9929,#d95f0e,#993404")
    };

    private static final Palette nominal19;                         // 19 of the 22 Kelly colors
    private static final Map<String, String> COLORS_BY_NAME;        // Colors by name

    static {
        COLORS_BY_NAME = new HashMap<String, String>();
        Scanner scanner = new Scanner(Palette.class.getResourceAsStream("/org/brunel/color/colors.txt")).useDelimiter("\n");

        List<String> boyntonColors = new ArrayList<String>();
        List<String> kellyColors = new ArrayList<String>();
        while (scanner.hasNext()) {
            String s = scanner.next();
            String[] parts = s.split("[\t]+");
            if (parts.length == 3) {
                String namespace = parts[0].trim();
                String name = parts[1].toLowerCase().trim();
                String def = parts[2].trim();
                String prev = COLORS_BY_NAME.put(name, def);
                if (prev != null && !prev.equals(def))
                    throw new IllegalStateException("Color file redefined " + name + ". Was " + prev + ", redefined to " + def);
                if (namespace.equalsIgnoreCase("boynton")) boyntonColors.add(def);
                if (namespace.equalsIgnoreCase("kelly")) kellyColors.add(def);
            }
        }
        if (kellyColors.size() != 19)
            throw new IllegalStateException("Expected 19 Kelly colors, but was " + kellyColors.size());
        nominal19 = new Palette("nominal19", kellyColors);
    }

    private final String name;
    private final Set<String> colorTags;
    private final String[] items;

    public Palette(String definition) {
        String[] parts = definition.split(";");
        this.name = parts[0].trim();
        this.colorTags = new HashSet<String>();
        for (String s : parts[1].split(",")) colorTags.add(s.trim());
        String[] cols = parts[2].split(",");
        items = new String[cols.length];
        for (int i = 0; i < items.length; i++) items[i] = cols[i].trim();
    }

    public static ColorMapping makeColorMapping(Field f, Param[] modifiers, boolean largeElement) {
        int mutingLevel = largeElement && f.preferCategorical() && !f.isBinned() ? 1 : 0;
        ColorMapping base;

        if (modifiers.length == 0) {
            base = makeDefaultMapping(f);
        } else {
            // Set the amount of mutedness of the hues
            String paletteName = null;
            paletteName = modifiers[0].asString();
            if (paletteName.endsWith("=")) {
                mutingLevel = 0;
                paletteName = paletteName.substring(0, paletteName.length() - 1);
            }
            while (paletteName.endsWith("*")) {
                mutingLevel++;
                paletteName = paletteName.substring(0, paletteName.length() - 1);
            }
            base = makeNamedMapping(f, paletteName);
        }

        // For binning the palette is based on the numeric values, but the categories
        // are not numeric, so we need to adapt the colors to fit the categories
        if (f.isBinned())
            base = base.fitColorsToCategories(f);

        // Mute the colors of the result if necessary
        return mutingLevel == 0 ? base : base.mute(mutingLevel);
    }

    private static ColorMapping makeNamedMapping(Field f, String paletteName) {
        String[] paletteParts = paletteName.split("-");
        if (paletteParts.length == 1) {
            String name = paletteParts[0];
            // Default name -- probably used just so we can mute the default
            if (name.equals("auto") || name.equals("default") || name.equals("")) return makeDefaultMapping(f);
            if (name.equals("diverging")) return makeNamedMapping(f, "Blue-Red");


            // Names for the nominal palette
            if (name.equalsIgnoreCase("nominal") || name.equalsIgnoreCase("ordinal") || name.equalsIgnoreCase("nominal19") || name.equalsIgnoreCase("kelly"))
                return makeNominal(f, nominal19.items);
            Palette palette = makeNamedPalette(name);
            return new ColorMapping(fieldSplits(f, palette.length(), null), palette.items);
        } else if (paletteParts.length == 2) {
            // Two range divergent
            String a = paletteParts[0];
            String b = paletteParts[1];
            return makeDivergent(f, makeNamedPalette(a), makeNamedPalette(b), null);
        } else {
            // Two range divergent with middle value
            String a = paletteParts[0];
            String b = paletteParts[2];
            String value = paletteParts[1];
            return makeDivergent(f, makeNamedPalette(a), makeNamedPalette(b), value);
        }
    }

    private int length() {
        return items.length;
    }

    private static ColorMapping makeDefaultMapping(Field f) {
        if (!f.isNumeric()) return makeNamedMapping(f, "nominal");

        // Default to divergent, except for given cases below
        boolean divergent = true;
        if (f.name.startsWith("#")) divergent = false;
        String summary = f.stringProperty("summary");
        if ("sum".equals(summary) || "percent".equals(summary)) divergent = false;

        // Ask for the appropriate named mapping
        return makeNamedMapping(f, divergent ? "Blue-Red" : "Blue");
    }

    private static Palette makeNamedPalette(String name) {
        if (name.startsWith("continuous") || name.startsWith("sequential")) {
            int n;
            try {
                n = Integer.parseInt(name.substring(name.length() - 1));
            } catch (NumberFormatException e) {
                // The default is the first one
                n = 0;
            }
            n = (n + sequential.length - 1) % sequential.length;
            return sequential[n];
        }

        for (Palette p : sequential)
            if (p.name.equalsIgnoreCase(name)) return p;

        if (name.startsWith("#") && name.length() == 7) return makeSingleHue(name);

        String color = COLORS_BY_NAME.get(name);
        if (color != null) return makeSingleHue(color);

        throw new IllegalStateException("Unknown color palette: " + name);

    }

    private static Palette makeSingleHue(String c) {
        java.awt.Color base = java.awt.Color.decode(c);
        float[] hsv = java.awt.Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), new float[3]);
        int n = 5;
        List<String> colors = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) {
            float r = i / (n - 1.0f);
            java.awt.Color a = java.awt.Color.getHSBColor(hsv[0], r * hsv[1], (1 - r) + r * hsv[2]);
            colors.add(String.format("#%02X%02X%02X", a.getRed(), a.getGreen(), a.getBlue()));
        }
        return new Palette(c, colors);
    }

    private static Object[] fieldSplits(Field f, int n, String midValue) {
        Auto.setTransform(f);
        String t = f.stringProperty("transform");
        Object[] objects = new Object[n];
        double min = f.min();
        double max = f.max();
        Double mid = midValue == null ? null : Double.parseDouble(midValue);
        for (int i = 0; i < n; i++) {
            if ("log".equals(t)) {
                objects[i] = Math.exp(interpolate(n, Math.log(min), Math.log(max), mid, i));
            } else if ("root".equals(t)) {
                objects[i] = Math.pow(interpolate(n, Math.sqrt(min), Math.sqrt(max), mid, i), 2);
            } else {
                objects[i] = interpolate(n, min, max, mid, i);
            }
        }
        return objects;
    }

    private static double interpolate(int n, double min, double max, Double mid, int i) {
        if (mid == null)
            return min + (max - min) * i / (n - 1);
        else {
            if (i <= (n + 1) / 2) return interpolate((n + 1) / 2, min, mid, null, i);
            else return interpolate((n + 1) / 2, mid, max, null, i - (n + 1) / 2);
        }
    }

    private static ColorMapping makeDivergent(Field f, Palette lower, Palette upper, String midValue) {
        int low = lower.items.length;
        int n = low + upper.items.length - 1;
        String[] colors = new String[n];
        for (int i = 0; i < n; i++) {
            if (i < low - 1) {
                colors[i] = lower.items[low - 1 - i];
            } else if (i == low - 1) {
                colors[i] = mid(lower.items[0], upper.items[0], 0.05);
            } else {
                colors[i] = upper.items[i - low];
            }
        }
        return new ColorMapping(fieldSplits(f, n, midValue), colors);
    }

    static String mid(String a, String b, double v) {
        // midway between two colors as hex strings -- just average the parts
        // they should be close, so we should not need to do anything complicated
        return "#" + mid2(a.substring(1, 3), b.substring(1, 3), v)
                + mid2(a.substring(3, 5), b.substring(3, 5), v) + mid2(a.substring(5, 7), b.substring(5, 7), v);
    }

    static String mid2(String hexA, String hexB, double v) {
        int a = Integer.parseInt(hexA, 16);
        int b = Integer.parseInt(hexB, 16);
        int mid = (int) Math.round(a * (1 - v) + b * v);
        String s = Integer.toHexString(mid);
        return s.length() < 2 ? "0" + s : s;
    }

    private static ColorMapping makeNominal(Field field, String[] items) {
        // Categorical data is easy -- just us the categories and we are done!
        if (field.preferCategorical())
            return new ColorMapping(field.categories(), items);

        // For numeric data we make bands of color
        int n = items.length;
        Object[] simple = fieldSplits(field, n, null);
        Object[] rampedValues = new Object[2 * n - 2];
        String[] rampedItems = new String[2 * n - 2];
        for (int i = 0; i < n - 1; i++) {
            rampedValues[2 * i] = simple[i];
            rampedValues[2 * i + 1] = simple[i + 1];
            rampedItems[2 * i] = items[i];
            rampedItems[2 * i + 1] = items[i];
        }
        return new ColorMapping(rampedValues, rampedItems);
    }

    public Palette(String name, List<String> colors) {
        this.name = name;
        this.colorTags = Collections.EMPTY_SET;
        this.items = colors.toArray(new String[colors.size()]);
    }
}
