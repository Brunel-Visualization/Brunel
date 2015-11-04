package org.brunel.color;

import org.brunel.data.Field;

/**
 * How to map a field to a color
 */
public class ColorMapping {
    public final Object[] values;
    public final String[] colors;

    public ColorMapping(Object[] values, String[] colors) {
        this.values = values;
        this.colors = colors;
    }

    public ColorMapping fitColorsToCategories(Field f) {
        Object[] newValues = f.categories();
        int n = newValues.length;
        String[] newColors = new String[n];
        for (int i = 0; i < n; i++)
            newColors[i] = interpolateMapping(i / (n - 1.0));
        return new ColorMapping(newValues, newColors);
    }

    private String interpolateMapping(double v) {
        int n = colors.length - 1;
        int a = (int) Math.floor(v * n);
        int b = (int) Math.ceil(v * n);
        double r = v * n - a;
        return Palette.mid(colors[a], colors[b], r);
    }

    public ColorMapping mute(int muted) {
        float amount = (float) Math.pow(0.7, muted);
        String[] newColors = new String[colors.length];
        for (int i = 0; i < colors.length; i++)
            newColors[i] = mute(colors[i], amount);
        return new ColorMapping(values, newColors);
    }

    private String mute(String color, float r) {
        java.awt.Color base = java.awt.Color.decode(color);
        float[] hsv = java.awt.Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), new float[3]);
        java.awt.Color a = java.awt.Color.getHSBColor(hsv[0], r * hsv[1], (1 - r) + r * hsv[2]);
        return String.format("#%02X%02X%02X", a.getRed(), a.getGreen(), a.getBlue());
    }

}
