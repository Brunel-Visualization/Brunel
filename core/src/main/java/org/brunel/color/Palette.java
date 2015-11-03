package org.brunel.color;

import org.brunel.action.Param;
import org.brunel.data.Field;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Palettes of color
 */
public class Palette {

    private static final Palette[] sequential = new Palette[]{
            new Palette("Green;blue,green;#edf8fb,#b2e2e2,#66c2a4,#2ca25f,#006d2c"),
            new Palette("Purple;blue,purple;#edf8fb,#b3cde3,#8c96c6,#8856a7,#810f7c"),
            new Palette("Blue;green,blue;#f0f9e8,#bae4bc,#7bccc4,#43a2ca,#0868ac"),
            new Palette("Red;orange,red;#fef0d9,#fdcc8a,#fc8d59,#e34a33,#b30000"),
            new Palette("Blue;blue,purple;#f1eef6,#bdc9e1,#74a9cf,#2b8cbe,#045a8d"),
            new Palette("Purple;red,purple;#feebe2,#fbb4b9,#f768a1,#c51b8a,#7a0177"),
            new Palette("Green;green;#ffffcc,#c2e699,#78c679,#31a354,#006837"),
            new Palette("Blue;green,blue;#ffffcc,#a1dab4,#41b6c4,#2c7fb8,#253494"),
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

        }

        if (modifiers.length == 1) {
            String name = modifiers[0].asString();
            if (name.equalsIgnoreCase("nominal9") || name.equalsIgnoreCase("boyton"))
                return makeNominal(f.categories(), nominal9.items);
            if (name.equalsIgnoreCase("nominal20") || name.equalsIgnoreCase("kelly"))
                return makeNominal(f.categories(), nominal20.items);
            if (name.equalsIgnoreCase("nominal")) {
                Palette palette = f.uniqueValuesCount() > 9 ? nominal20 : nominal9;
                return makeNominal(f.categories(), palette.items);
            }
        }

        return null;

//        int n = f.numericProperty("unique").intValue();
//
//        // Ordinal first
//        boolean ordinal = "ordinal".equals(hint) || f.preferCategorical() && f.isNumeric();
//        if (ordinal) return ordinalPalette(n);
//
//        // If categorical or binary, use the nominal palette
//        if (n < 3 || f.preferCategorical()) return NOMINAL;
//
//        /// Default to divergent, except for given cases below
//        boolean divergent = true;
//        if (f.name.startsWith("#")) divergent = false;
//        String summary = f.stringProperty("summary");
//        if ("sum".equals(summary) || "percent".equals(summary)) divergent = false;
//
//        if (divergent) return DIVERGING;
//        else return CONTINUOUS;
    }

    private static ColorMapping makeNominal(Object[] categories, String[] items) {
        return new ColorMapping(categories, items);
    }

    public Palette(String name, Color[] colors) {
        this.name = name;
        this.colorTags = Collections.EMPTY_SET;
        this.items = new String[colors.length];
        for (int i = 0; i < items.length; i++) items[i] = colors[i].hexCode;
    }
}
