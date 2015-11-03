package org.brunel.color;

import org.brunel.action.Param;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;

import java.util.Collections;
import java.util.HashSet;
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

    private static final Palette nominal9 = new Palette("nominal9", Color.boynton);
    private static final Palette nominal20 = new Palette("nominal20", Color.kelly);

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

    public static ColorMapping makeColorMapping(Field f, Param[] modifiers) {

        if (modifiers.length == 0) {
            if (f.preferCategorical()) {
                Palette palette = f.uniqueValuesCount() > 8 ? nominal20 : nominal9;
                return makeNominal(f, palette.items);
            }

            // Default to divergent, except for given cases below
            boolean divergent = true;
            if (f.name.startsWith("#")) divergent = false;
            String summary = f.stringProperty("summary");
            if ("sum".equals(summary) || "percent".equals(summary)) divergent = false;
            return divergent ? makeDivergent(f, sequential[3], sequential[4]) : makeSequential(f, sequential[0]);
        }

        String[] paletteParts = modifiers[0].asString().split("-");

        if (paletteParts.length == 1) {
            String name = paletteParts[0];
            if (name.equalsIgnoreCase("nominal8") || name.equalsIgnoreCase("boyton"))
                return makeNominal(f, nominal9.items);
            if (name.equalsIgnoreCase("nominal20") || name.equalsIgnoreCase("kelly"))
                return makeNominal(f, nominal20.items);
            if (name.equalsIgnoreCase("nominal")) {
                Palette palette = f.uniqueValuesCount() > 9 ? nominal20 : nominal9;
                return makeNominal(f, palette.items);
            }
            return makeSequential(f, makeNamedPalette(name));
        } else {
            String a = paletteParts[0];
            String b = paletteParts[1];
            return makeDivergent(f, makeNamedPalette(a), makeNamedPalette(b));
        }

    }

    private static Palette makeNamedPalette(String name) {
        if (name.startsWith("sequential")) {
            int n;
            try {
                n = Integer.parseInt(name.substring("sequential".length()));
            } catch (NumberFormatException e) {
                // The default one
                n = 0;
            }
            n = (n + sequential.length - 1) % sequential.length;
            return sequential[n];
        }

        for (Palette p : sequential)
            if (p.name.equalsIgnoreCase(name)) return p;
        for (Color c : Color.boynton)
            if (c.name.equalsIgnoreCase(name)) return makePalette(c);
        for (Color c : Color.kelly)
            if (c.name.equalsIgnoreCase(name)) return makePalette(c);
        for (Color c : Color.general)
            if (c.name.equalsIgnoreCase(name)) return makePalette(c);
        throw new IllegalStateException("Unknown color palette: " + name);

    }

    private static Palette makePalette(Color c) {
        java.awt.Color base = java.awt.Color.decode(c.hexCode);
        float[] hsv = java.awt.Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), new float[3]);
        int n = 5;
        Color[] colors = new Color[n];
        for (int i = 0; i < n; i++) {
            float r = i / (n - 1.0f);
            java.awt.Color a = java.awt.Color.getHSBColor(hsv[0], r * hsv[1], (1 - r) + r * hsv[2]);
            String s = String.format("#%02X%02X%02X", a.getRed(), a.getGreen(), a.getBlue());
            colors[i] = new Color(s, s);
        }
        return new Palette(c.name, colors);
    }

    private static ColorMapping makeSequential(Field f, Palette palette) {
        int n = palette.items.length;
        return new ColorMapping(fieldSplits(f, n), palette.items);
    }

    private static Object[] fieldSplits(Field f, int n) {
        Auto.setTransform(f);
        String t = f.stringProperty("transform");
        Object[] objects = new Object[n];
        double min = f.min();
        double max = f.max();
        for (int i = 0; i < n; i++) {
            if ("log".equals(t))
                objects[i] = Math.exp(Math.log(min) + (Math.log(max) - Math.log(min)) * i / (n - 1));
            else if ("root".equals(t))
                objects[i] = Math.pow(Math.sqrt(min) + (Math.sqrt(max) - Math.sqrt(min)) * i / (n - 1), 2);
            else
                objects[i] = min + (max - min) * i / (n - 1);
        }
        return objects;
    }

    private static ColorMapping makeDivergent(Field f, Palette lower, Palette upper) {
        int low = lower.items.length;
        int n = low + upper.items.length - 1;
        String[] colors = new String[n];
        for (int i = 0; i < n; i++) {
            if (i < low - 1) {
                colors[i] = lower.items[low - 1 - i];
            } else if (i == low - 1) {
                colors[i] = mid(lower.items[0], upper.items[0]);
            } else {
                colors[i] = upper.items[i - low];
            }
        }
        return new ColorMapping(fieldSplits(f, n), colors);
    }

    private static String mid(String a, String b) {
        // midway between two colors as hex strings -- just average the parts
        // they should be close, so we should not need to do anything complicated
        return "#" + mid2(a.substring(1, 3), b.substring(1, 3))
                + mid2(a.substring(3, 5), b.substring(3, 5)) + mid2(a.substring(5, 7), b.substring(5, 7));
    }

    private static String mid2(String hexA, String hexB) {
        int a = Integer.parseInt(hexA, 16);
        int b = Integer.parseInt(hexB, 16);
        int mid = (int) Math.round((a + b) / 2.0);
        String s = Integer.toHexString(mid);
        return s.length() < 2 ? "0" + s : s;
    }

    private static ColorMapping makeNominal(Field field, String[] items) {
        // Categorical data is easy -- just us the categories and we are done!
        if (field.preferCategorical())
            return new ColorMapping(field.categories(), items);

        // For numeric data we make bands of color
        int n = items.length;
        Object[] simple = fieldSplits(field, n);
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

    public Palette(String name, Color[] colors) {
        this.name = name;
        this.colorTags = Collections.EMPTY_SET;
        this.items = new String[colors.length];
        for (int i = 0; i < items.length; i++) items[i] = colors[i].hexCode;
    }
}
