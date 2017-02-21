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

package org.brunel.build.util;

import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleSheet;
import org.brunel.model.style.StyleTarget;

/**
 * Utilities for VisItem and model items
 */
public class ModelUtil {

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
     * @param vis    the visualization to look for definitions in
     * @param target style to look for
     * @param tag    height, width, size -- the name of the size field to look for
     * @return a Size describing it
     */
    public static Size getSize(VisSingle vis, StyleTarget target, String tag) {
        String s = getStyle(vis, target, tag);
        if (s == null) {
            if (tag.equals("height") || tag.equals("width"))
                return getSize(vis, target, "size");            // height and width default to size
            else
                return null;
        }
        return decompose(s);                                    // Split into value and unit
    }

    /**
     * Returns the symbol to use for an element as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @return "rect", "circle", etc.
     */
    public static String getSymbolFromStyle(VisSingle vis) {
        StyleTarget target = StyleTarget.makeElementTarget(null, "element", "point");
        return vis.styles == null ? null : vis.styles.get(target, "symbol");
    }

    /**
     * Returns the font size of the element as defined by the style
     *
     * @param vis the visualization to look for definitions in
     * @return integer size
     */
    public static double getFontSize(VisSingle vis, StyleTarget target, int defaultSize) {
        Size size = getSize(vis, target, "font-size");
        return size == null ? defaultSize : size.value(defaultSize);
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
     * Returns the label position
     *
     * @param vis the visualization to look for definitions in
     * @return null if not defined
     */
    public static String getLabelPosition(VisSingle vis) {
        if (vis == null) return null;
        String s = getStyle(vis, StyleTarget.makeElementTarget(null, "element"), "label-location");
        if (s != null) return s;
        return getStyle(vis, StyleTarget.makeElementTarget(null, "label"), "label-location");
    }

    public static String getLabelAlignment(VisSingle vis) {
        String s = getStyle(vis, StyleTarget.makeElementTarget(null, "element"), "text-align");
        if (s != null) return s;
        return getStyle(vis, StyleTarget.makeElementTarget(null, "label"), "text-align");
    }

    public static double getLabelPadding(VisSingle vis, int defaultValue) {
        Size s = getSize(vis, StyleTarget.makeElementTarget(null, "element"), "padding");
        if (s == null) s = getSize(vis, StyleTarget.makeElementTarget(null, "label"), "padding");
        return s == null ? defaultValue : s.value(defaultValue);
    }

    /**
     * Returns the title position (defaults to center)
     *
     * @param vis the visualization to look for definitions in
     */
    public static String getTitlePosition(VisSingle vis, StyleTarget target) {
        String s = getStyle(vis, target, "label-location");
        return s == null ? "center" : s;
    }

    public static Padding getPadding(VisSingle vis, StyleTarget target, int defaultSize) {
        int top = defaultSize, left = defaultSize, bottom = defaultSize, right = defaultSize;

        // Global size
        String s = getStyle(vis, target, "padding");
        if (s != null)
            top = left = right = bottom = (int) decompose(s).value((double) 100);

        // Top
        s = getStyle(vis, target, "padding-top");
        if (s != null) top = (int) decompose(s).value((double) 100);
        // Bottom
        s = getStyle(vis, target, "padding-bottom");
        if (s != null) bottom = (int) decompose(s).value((double) 100);
        // Left
        s = getStyle(vis, target, "padding-left");
        if (s != null) left = (int) decompose(s).value((double) 100);
        // Right
        s = getStyle(vis, target, "padding-right");
        if (s != null) right = (int) decompose(s).value((double) 100);

        return new Padding(top, left, bottom, right);
    }

    public static double getSize(VisSingle vis, StyleTarget target, String tag, double defaultSize) {
        Size size = getSize(vis, target, tag);
        return size == null ? defaultSize : size.value(defaultSize);
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

        public String suffix() {
            // All are converted to pixels
            return isPercent() ? unit : "px";
        }

        public boolean isPercent() {
            return unit.equals("%");
        }

        public String toString() {
            return value + unit;
        }

        public double value(double percentSize100) {
            if (isPercent()) return value * percentSize100 / 100;
            if (unit.equals("px")) return value;
            if (unit.equals("mm")) return value * DPI / 25.4;
            if (unit.equals("cm")) return value * DPI / 2.54;
            if (unit.equals("in")) return value * DPI;
            if (unit.equals("pt")) return value * DPI / 72;
            if (unit.equals("pc")) return value * DPI / 6;
            throw new IllegalStateException("Unknown unit: " + unit);
        }
    }
}
