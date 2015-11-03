package org.brunel.color;

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
}
