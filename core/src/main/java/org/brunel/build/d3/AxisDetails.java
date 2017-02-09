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

package org.brunel.build.d3;

import org.brunel.build.d3.titles.AxisTitleBuilder;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.util.Range;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleTarget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Calculates information on how to display axes
 */
public class AxisDetails {

    public final String title;                         // Title for the axis
    public final String scale;                         // Name for the scale to use for this axis
    public final boolean hasGrid;                      // true if gridlines are desired
    public final StyleTarget styleTarget;              // style to target the axis
    public boolean rotatedTicks;                       // If true, ticks are to be rotated
    public Object[] tickValues;                        // If non-null, ony show these ticks
    public Integer tickCount;                          // If non-null, request this many ticks for the axis
    public int size;                                   // The size for this axis (perpendicular to axis direction)
    public int leftGutter;
    public int rightGutter;                            // Space needed on left and right (for horizontal chart only)
    public int topGutter;
    public int bottomGutter;                            // Space above and below chart (for vertical chart only)

    public int fontSize;                                // font height
    public Padding tickPadding;                         // Padding for the ticks
    public int markSize;                                // Size of the tick mark

    private final Field[] fields;                       // Fields used in this axis
    public final boolean categorical;                  // True if the axis is categorical
    public final boolean inMillions;                   // True if the fields values are nicely shown in millions
    private AxisTitleBuilder titleBuilder;              // builds the title for this axis

    /* Constructs the axis for the given fields */
    public AxisDetails(String dimension, Field[] definedFields, boolean categorical, String userTitle, int tickCount, boolean grid) {
        this.scale = "scale_" + dimension;
        this.fields = definedFields;
        this.categorical = categorical;
        this.tickCount = tickCount < 100 ? tickCount : null;

        if (userTitle != null)
            this.title = (userTitle.isEmpty() ? null : userTitle);
        else
            this.title = title(fields);

        this.inMillions = !categorical && isInMillions(definedFields);
        this.hasGrid = grid;
        this.styleTarget = StyleTarget.makeTopLevelTarget("g", "axis", isX() ? "x" : "y");
    }

    public boolean isX() {
        return scale.endsWith("x");
    }

    public void setAdditionalHAxisOffset(double additionalHAxisOffset) {
        if (exists()) titleBuilder.bottomOffset = additionalHAxisOffset;
    }

    public void setTextDetails(ChartStructure structure, boolean isHorizontal) {
        if (!exists()) return;
        VisSingle vis = findLikelyElement(structure);
        titleBuilder = new AxisTitleBuilder(vis, this, isHorizontal);

        StyleTarget tick = StyleTarget.makeTarget("g", styleTarget, "tick");    // tick  group
        StyleTarget mark = StyleTarget.makeTarget("line", tick);                // the mark within the tick
        StyleTarget text = StyleTarget.makeTarget("text", tick);                // the text within the tick

        tickPadding = ModelUtil.getPadding(vis, text, 3);                       // padding for tick text
        fontSize = (int) ModelUtil.getFontSize(vis, text, 12);                  // font size
        markSize = (int) ModelUtil.getSize(vis, mark, "size", 3);               // mark size
    }

    /**
     * Find the first element that describes an axis, or just the first element if none do
     *
     * @param structure the chart elements
     * @return a VisSingle -- will not be null
     */
    private VisSingle findLikelyElement(ChartStructure structure) {
        // Look for the first element defining axes
        for (VisSingle vis : structure.elements)
            if (!vis.fAxes.isEmpty()) return vis;

        // Look for the first vis defining any styles
        for (VisSingle vis : structure.elements)
            if (vis.styles != null) return vis;

        // Just use the first
        return structure.elements[0];
    }

    /**
     * Add the title definition to the designated axis group element
     *
     * @param group svg group for the element
     * @param out   writer
     */
    public void writeTitle(String group, ScriptWriter out) {
        titleBuilder.writeContent(group, out);
    }

    private boolean isInMillions(Field[] definedFields) {
        for (Field f : definedFields) {
            if (!f.isDate() && f.max() != null && f.max() - f.min() > 2e6) return true;
        }
        return false;
    }

    /* Return the title for the axis */
    private static String title(Field[] fields) {

        // Get all the valid fields
        List<Field> real = new ArrayList<>();
        for (Field f : fields) if (!f.isSynthetic() && !f.name.startsWith("'")) real.add(f);

        LinkedHashSet<String> titles = new LinkedHashSet<>();             // All the titles
        LinkedHashSet<String> originalTitles = new LinkedHashSet<>();     // Only using names before summary
        for (Field f : real) {
            titles.add(f.label);
            String originalLabel = (String) f.property("originalLabel");
            originalTitles.add(originalLabel == null ? f.label : originalLabel);
        }
        if (originalTitles.size() < titles.size()) titles = originalTitles;     // If shorter, use that
        return titles.isEmpty() ? null : Data.join(titles);
    }

    /**
     * Layout the axis so it fills the available space
     *
     * @param availableSpace space into which we can be drawn
     * @param fillToEdge     if true, ticks will go to the edges, rather than be in the middle as usual
     */
    public void layoutHorizontally(double availableSpace, boolean fillToEdge) {
        if (!exists()) return;

        int tickWidth = maxCategoryWidth() + 5;
        if (tickWidth > availableSpace * 0.5) tickWidth = (int) (availableSpace * 0.5);
        int tickCount = countTicks(fields);

        if (!categorical) fillToEdge = true;        // Numeric ticks may go right to edge

        // When we fill to the edge, we will need to inset by a bit to account for the ticks
        if (fillToEdge) availableSpace -= tickWidth;

        int spaceForOneTick = (int) (availableSpace / tickCount);

        if (categorical && availableSpace < tickWidth * tickCount) {
            // We must use rotated ticks, and may also need only to show a subset of them
            rotatedTicks = true;
            tickValues = makeSkippingTickValues(availableSpace, tickCount);
            int tickHeight = (int) (tickWidth / Math.sqrt(2));

            size = tickHeight + fontSize + spaceForMarks() + estimatedTitleHeight();
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

            // Do not override user-set value for tick count
            if (availableSpace < tickWidth * tickCount && this.tickCount == null) {
                // Must be numeric in this case, so reduce the desired number of ticks for that axis
                this.tickCount = (int) (availableSpace / (tickWidth + 5));
            }
        }
    }

    /* Does not exist if no fields to show */
    public boolean exists() {
        return fields.length > 0;
    }

    /* Estimate the space needed to show all text categories */
    public int maxCategoryWidth() {
        if (fields.length == 0) return 0;
        int maxCharCount = 1;
        for (Field f : fields) {
            if (categorical) {
                for (Object s : f.categories()) {
                    int length = f.format(s).length();
                    if (s instanceof Range) length++;                       // The ellipsis is often rather long
                    maxCharCount = Math.max(maxCharCount, length);
                }
            } else {
                // If we have numeric data, we use our scaling to guess the divisions needed
                Object[] sampleTicks = Auto.makeNumericScale(f, true, new double[]{0, 0}, 0, 5, false).divisions;
                for (Object s : sampleTicks)
                    maxCharCount = Math.max(maxCharCount, f.format(s).length());
                // Always allow room for three characters. We need this because D3 often
                // adds fractions to what should be integer scales
                maxCharCount = Math.max(maxCharCount, 3);
            }
        }
        return estimatedTickLength(maxCharCount);
    }

    private int countTicks(Field[] fields) {
        if (!categorical) return 10;                            // Assume 10 ticks for numeric
        if (fields.length == 0) return 1;                       // To prevent div by zero errors
        int n = 0;
        for (Field f : fields)
            n += f.categories().length;
        return n;
    }

    // If needed, create ticks that will fit the space nicely
    private Object[] makeSkippingTickValues(double width, int count) {
        if (!categorical) return null;    // Only good for categorical
        double spacePerTick = width / count;
        int skipFrequency = (int) Math.round((fontSize * 3/2 + tickPadding.vertical()) / spacePerTick);
        if (skipFrequency < 2) return null;
        List<Object> useThese = new ArrayList<>();
        int at = 0;
        for (Field f : fields) {
            for (Object s : f.categories())
                if (at++ % skipFrequency == 0) useThese.add(s);
        }
        return useThese.toArray(new Object[useThese.size()]);
    }

    /* Space needed for title */
    private int estimatedTitleHeight() {
        return (int) titleBuilder.verticalSpace();
    }

    public int estimatedSimpleSizeWhenHorizontal() {
        return exists() ? fontSize + tickPadding.vertical() + spaceForMarks() + estimatedTitleHeight() : 0;
    }

    // A negative mark size is drawn inside, but we don't want to subtract the size!
    private int spaceForMarks() {
        return Math.max(markSize,0);
    }

    public void layoutVertically(double availableSpace) {
        if (!exists()) return;
        int tickCount = countTicks(fields);

        // A simple fixed gutter for ticks to flow into
        topGutter = 5;
        bottomGutter = 5;
        availableSpace -= (topGutter + bottomGutter);

        // Simple algorithm: just skip items if necessary
        if (categorical) {
            tickValues = makeSkippingTickValues(availableSpace, tickCount);
        } else {
            int tickHeight = fontSize * 3/4;
            if (tickHeight * tickCount > availableSpace && this.tickCount == null) {
                this.tickCount = (int) (availableSpace / tickHeight);
            }

        }

        // Add 6 pixels for gap between title and ticks
        size = tickValues == null ? maxCategoryWidth() : maxTickWidth();
        size += estimatedTitleHeight() + spaceForMarks();
    }

    /* Estimate the space needed to show all text categories */
    private int maxTickWidth() {
        int maxCharCount = 1;
        for (Object s : tickValues) {
            int length = s.toString().length();
            if (s instanceof Range) length++;                   // The ellipsis is often rather long
            maxCharCount = Math.max(maxCharCount, length);
        }
        return estimatedTickLength(maxCharCount);
    }

    private int estimatedTickLength(int maxCharCount) {
        // Assume a font with about this character width
        return tickPadding.horizontal() + maxCharCount * (fontSize + 1) / 2;
    }

}
