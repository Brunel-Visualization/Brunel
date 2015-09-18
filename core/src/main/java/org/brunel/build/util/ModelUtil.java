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

package org.brunel.build.util;

import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleSheet;
import org.brunel.model.style.StyleTarget;

/**
 * Utilities for VisItem and model items
 */
public class ModelUtil {

    /* Top level style targets -- all targets should parent to this */
    private static final StyleTarget STYLE_TOP = new StyleTarget("svg", null, "brunel");
    private static final StyleTarget STYLE_ELEMENT = new StyleTarget(null, STYLE_TOP, "currentElement");

    /**
     * Determine if the fields are best represented by a categorical (as opposed to numeric) scale
     *
     * @param fields fields to analyze
     * @return true if we should be categorical, false if not.
     */
    public static boolean combinationIsCategorical(Field[] fields, boolean preferContinuous) {
        boolean allPreferCategorical = true;
        for (Field f : fields) {
            if (!f.preferCategorical()) allPreferCategorical = false;
            if (!f.isNumeric()) return true;        // Cannot be numeric, so combination must be categorical
        }

        // If we prefer continuous data, do that if at all possible
        if (preferContinuous) return false;

        // If everything wants to be categorical,we choose categorical
        return allPreferCategorical;
    }

    /**
     * Returns the size of the element as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @param tag height, width, size -- the name of the size field to look for
     * @return a Size describing it
     */
    public static Size getElementSize(VisSingle vis, String tag) {
        StyleTarget target = new StyleTarget(null, STYLE_ELEMENT, "element");
        String s = getStyle(vis, target, tag);
        if (s == null)
            if (tag.equals("size")) return null;            // No definition
            else return getElementSize(vis, "size");        // Default height and width to size
        return decompose(s);                                // Split into value and unit
    }

    /**
     * Returns the symbol to use for an element as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @return "rect", "circle", etc.
     */
    public static String getElementSymbol(VisSingle vis) {
        StyleTarget target = new StyleTarget(null, STYLE_ELEMENT, "element", "point");
        return vis.styles == null ? null : vis.styles.get(target, "symbol");
    }

    /**
     * Returns the font size of the element as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @return a Size describing it
     */
    public static Size getFontSize(VisSingle vis) {
        String s = getStyle(vis, new StyleTarget("text", STYLE_ELEMENT, "element"), "font-size");
        return decompose(s);
    }

    /**
     * Returns the font size of the label for the axis as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @return a Size describing it
     */
    public static Size getAxisLabelFontSize(VisSingle vis) {
        String s = getStyle(vis, new StyleTarget("text", STYLE_ELEMENT, "axis", "label"), "font-size");
        return decompose(s);
    }

    /**
     * Returns the corner radius forthe element as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @return a Size describing it
     */
    public static Size getRoundRectangleRadius(VisSingle vis) {
        String s = getStyle(vis, new StyleTarget("rect", STYLE_ELEMENT, "element", "point"), "border-radius");
        return decompose(s);
    }

    private static Size decompose(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            int pUnit = s.length();
            while (pUnit > 0 && "1234567890.".indexOf(s.charAt(pUnit - 1)) < 0)
                pUnit--;
            double v = Double.parseDouble(s.substring(0, pUnit));
            return new Size(v, s.substring(pUnit));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse style defined as: " + s, e);
        }
    }

    /**
     * Get the value of a style item
     *
     * @param vis    which visualization to look at styles for
     * @param target the target style to look for
     * @param key    which value to return
     * @return the found value in either the vis styles, or the default styles, or null if not found in either
     */
    private static String getStyle(VisSingle vis, StyleTarget target, String key) {
        String result = vis.styles == null ? null : vis.styles.get(target, key);
        return result == null ? StyleSheet.getBrunelDefault(target, key) : result;
    }

    // Split a string into the numeric and unit parts
    private static String[] splitByUnit(String text) {
        if (text == null) return new String[]{"12", "px"};
        int p = text.length();
        while (p > 0)
            if (Character.isDigit(text.charAt(--p))) break;
        if (p == text.length() - 1) return new String[]{text, "px"};
        return new String[]{text.substring(0, p + 1), text.substring(p + 1)};
    }

    /**
     * Defines a value and unit for that value
     */
    public static final class Size {

        private static final int DPI = 96;
        private final double value;
        private final String unit;

        public Size(double value, String unit) {
            this.value = value;
            this.unit = unit.isEmpty() ? "px" : unit;
        }

        public boolean isPercent() {
            return unit.equals("%");
        }

        public String toString() {
            return value + unit;
        }

        public String suffix() {
            // All are converted to pixels
            return isPercent() ? unit : "px";
        }

        public double value() {
            if (isPercent()) return value / 100;
            if (unit.equals("px")) return value;
            if (unit.equals("mm")) return value * DPI / 25.4;
            if (unit.equals("cm")) return value * DPI / 2.54;
            if (unit.equals("in")) return value * DPI;
            if (unit.equals("pt")) return value * DPI / 72;
            if (unit.equals("pc")) return value * DPI / 6;
            throw new IllegalStateException("Unknown unit: " + unit);
        }

        public double valueInPixels(double percentSize100) {
            return isPercent() ? value() * percentSize100 : value;
        }
    }
}
