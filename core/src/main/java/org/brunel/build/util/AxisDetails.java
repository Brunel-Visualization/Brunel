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

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.util.Range;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates information on how to display axes
 */
public class AxisDetails {

    /* Estimate the space needed to show all text categories */
    public static int maxCategoryWidth(Field... fields) {
        if (fields.length == 0) return 0;
        int maxCharCount = 1;
        for (Field f : fields) {
            if (f.preferCategorical()) {
                for (Object s : f.categories()) {
                    int length = f.format(s).length();
                    if (s instanceof Range) length++;                       // The ellipsis is often rather long
                    maxCharCount = Math.max(maxCharCount, length);
                }
            } else {
                // If we have numeric data, we use our scaling to guess the divisions needed
                Object[] sampleTicks = Auto.makeNumericScale(f, true, new double[]{0,0}, 0, 5, false).divisions;
                for (Object s : sampleTicks)
                    maxCharCount = Math.max(maxCharCount, f.format(s).length());
                // Always allow room for three characters. We need this because D3 often
                // adds fractions to what should be integer scales
                maxCharCount = Math.max(maxCharCount, 3);
            }
        }
        return (int) (maxCharCount * 6.5);      // Assume a font with about this character width
    }

    private final Field[] fields;                      // Fields used in this axis
    public final String title;                         // Title for the axis
    public final String scale;                         // Name for the scale to use for this axis
    private final boolean categorical;                 // True if the axis is categorical
    public boolean rotatedTicks = false;               // If true, ticks are to be rotated
    public Object[] tickValues;                        // If non-null, ony show these ticks
    public int size;                                   // The size for this axis (perpendicular to axis direction)
    public int leftGutter;
    public int rightGutter;                // Space needed on left and right (for horizontal chart only)
    public int topGutter;
    public int bottomGutter;                // Space above and below chart (for vertical chart only)

    /* Constructs the axis for the given fields */
    public AxisDetails(String dimension, Field[] definedFields) {
        this.scale = "scale_" + dimension;
        this.fields = suitable(definedFields);
        this.title = title(fields);
        this.categorical = fields.length > 0 && fields[0].preferCategorical();
    }

    private Field[] suitable(Field[] fields) {
        Set<Field> result = new LinkedHashSet<Field>();
        for (Field f: fields) {
            if (f.name.startsWith("'")) continue;           // Do not use constants
            result.add(f);
        }
        return result.toArray(new Field[result.size()]);
    }

    public boolean isLog() {
        return fields.length > 0 && "log".equals(fields[0].property("transform"));
    }

    public void layoutVertically(double availableSpace) {
        if (!exists()) return;
        int tickCount = countTicks(fields);

        // A simple fixed gutter for ticks to flow into
        topGutter = 5;
        bottomGutter = 5;
        availableSpace -= (topGutter + bottomGutter);

        // Simple algorithm: just skip fields if necessary
        tickValues = makeSkippingTickValues(availableSpace, tickCount);

        // Add 10 pixels for tick marks and gap between title and ticks
        size = maxCategoryWidth(fields) + estimatedTitleHeight() + 10;
    }

    /**
     * Layout the axis so it fills the available space
     *
     * @param availableSpace space into which we can be drawn
     * @param fillToEdge     if true, ticks will go to the edges, rather than be in the middle as usual
     */
    public void layoutHorizontally(double availableSpace, boolean fillToEdge) {
        if (!exists()) return;

        int tickWidth = maxCategoryWidth(fields);
        int tickCount = countTicks(fields);

        if (!categorical) fillToEdge = true;        // Numeric ticks may go right to edge

        // When we fill to the edge, we will need to inset by a bit to account for the ticks
        if (fillToEdge) availableSpace -= tickWidth;

        int spaceForOneTick = (int) (availableSpace / tickCount);

        if (tickWidth > spaceForOneTick + 5) {
            // We must use rotated ticks, and may also need only to show a subset of them
            rotatedTicks = true;
            tickValues = makeSkippingTickValues(availableSpace, tickCount);
            int tickHeight = (int) (tickWidth / Math.sqrt(2));
            size = tickHeight + 16 + estimatedTitleHeight();
            if (fillToEdge) {
                rightGutter = 10;                       // Our ticks are offset about 6-8 pixels to right
                leftGutter = tickHeight - 8;            // Since it's diagonal, width == height
            } else {
                rightGutter = Math.max(0, 10 - spaceForOneTick / 2);
                leftGutter = Math.max(0, tickHeight - 8 - spaceForOneTick / 2);
            }
        } else {
            // Simple horizontal layout
            size = estimatedSimpleSizeWhenHorizontal();
            if (fillToEdge)
                leftGutter = tickWidth / 2;
            else
                leftGutter = Math.max(0, tickWidth / 2 - spaceForOneTick / 2);
            rightGutter = leftGutter;
        }
    }

    public int estimatedSimpleSizeWhenHorizontal() {
        return exists() ? 20 + estimatedTitleHeight() : 0;
    }

    /* Does not exist if no fields to show */
    public boolean exists() {
        return fields.length > 0;
    }

    /* Space needed for title */
    private int estimatedTitleHeight() {
        return title == null ? 0 : 16;
    }

    /* Return the title for the axis */
    private static String title(Field[] fields) {
        if (fields.length == 1) {
            // No title for simple count axis
            if (fields[0].name.equals("#count")) return null;
        }

        List<String> titles = new ArrayList<String>();
        for (Field f : fields) {
            // If a field title is repeated, we only use it once. This might happen for multiple elements
            // in the same coordinate system or for lower/upper field pairs for ranges
            if (!titles.contains(f.label)) titles.add(f.label);
        }
        return titles.isEmpty() ? null : Data.join(titles);
    }

    private static int countTicks(Field[] fields) {
        if (fields.length == 0) return 1;                               // To prevent div by zero errors
        int n = 0;
        for (Field f : fields)
            n += f.preferCategorical() ? f.categories().length : 5;     // Assume 5 ticks for numeric
        return n;
    }

    // If needed, create ticks that will fit the space nicely
    private Object[] makeSkippingTickValues(double width, int count) {
        double spacePerTick = width / count;
        int skipFrequency = (int) Math.round(20 / spacePerTick);
        if (skipFrequency < 2) return null;
        List<Object> useThese = new ArrayList<Object>();
        int at = 0;
        for (Field f : fields) {
            if (!f.preferCategorical()) return null;                    // Only good for categorical
            for (Object s : f.categories())
                if (at++ % skipFrequency == 0) useThese.add(s);
        }
        return useThese.toArray(new Object[useThese.size()]);
    }

}
