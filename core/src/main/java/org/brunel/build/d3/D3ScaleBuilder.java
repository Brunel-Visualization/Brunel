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

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.Palette;
import org.brunel.build.util.AxisDetails;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.auto.NumericScale;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds scales and axes; also guesses the right size to leave for axes
 * <p/>
 * IMPORTANT NOTE:
 * <p/>
 * The terms 'x' and 'y' both apply to the theoretical location. Whe we transpose a chart we move
 * axes and dimensions around, so we cannot always say 'x' runs horizontally. So in the code below
 * 'x' and 'y' are used only for the untransformed locations. We use left, right, top, bottom, h and v
 * for the transformed ones
 */
class D3ScaleBuilder {

    private enum Purpose {x, y, size, color}                // scale purpose

    final VisTypes.Coordinates coords;                      // Combined coordinate system derived from all elements
    final boolean isDiagram;                                // True id we want to treat it as a diagram coord system

    private final Field colorLegendField;                   // Field to use for the color legend
    private final AxisDetails hAxis, vAxis;                 // The same as the above, but at the physical location
    private final double[] marginTLBR;                      // Margins between the coordinate area and the chart space
    private final VisSingle[] element;                      // The elements ...
    private final Dataset[] elementData;                    // ... and their data
    private final PositionFields positionFields;
    private final ScriptWriter out;                         // Write definitions to here

    public D3ScaleBuilder(VisSingle[] element, Dataset[] elementData, PositionFields positionFields, double chartWidth, double chartHeight, ScriptWriter out) {
        this.element = element;
        this.elementData = elementData;
        this.positionFields = positionFields;
        this.out = out;
        this.isDiagram = chooseIsDiagram();
        this.coords = makeCombinedCoords();
        VisTypes.Axes axes = makeCombinedAxes();

        // Create the position needed
        this.colorLegendField = getColorLegendField();

        // Set the axis information for each dimension
        AxisDetails xAxis, yAxis;
        if (axes == VisTypes.Axes.all || axes == VisTypes.Axes.x) {
            xAxis = new AxisDetails("x", positionFields.allXFields);
        } else
            xAxis = new AxisDetails("x", new Field[0]);
        if (axes == VisTypes.Axes.all || axes == VisTypes.Axes.y)
            yAxis = new AxisDetails("y", positionFields.allYFields);
        else
            yAxis = new AxisDetails("y", new Field[0]);

        // Map the dimension to the physical location on screen
        if (coords == VisTypes.Coordinates.transposed) {
            hAxis = yAxis;
            vAxis = xAxis;
        } else {
            hAxis = xAxis;
            vAxis = yAxis;
        }

        int legendWidth = legendWidth();

        /*
            We have a slight chicken-and-egg situation here. To layout any axis, we need to
            know the available space for it. But to do that we need to know the size of the
            color axis. But to do that we need to lay out the color axis ...
            To resolve this, we make a very simple guess for the horizontal axis, then
            layout the vertical axis based on that, then layout the horizontal
         */

        vAxis.layoutVertically(chartHeight - hAxis.estimatedSimpleSizeWhenHorizontal());
        hAxis.layoutHorizontally(chartWidth - hAxis.size - legendWidth, allElementsLikeFillingToEdge());

        // Set the margins
        int marginTop = vAxis.topGutter;                                    // Only the vAxis needs space here
        int marginLeft = Math.max(vAxis.size, hAxis.leftGutter);            // Width of vAxis, or horizontal gutter
        int marginBottom = Math.max(hAxis.size, vAxis.bottomGutter);        // Height of hAxis, or gutter for vAxis
        int marginRight = Math.max(hAxis.rightGutter, legendWidth);         // Overflow for hAxis, or legend
        marginTLBR = new double[]{marginTop, marginLeft, marginBottom, marginRight};

    }

    /**
     * Adds the calls to set the axes into the already defined scale groups
     */
    private void defineAxesBuild() {
        out.onNewLine().ln().add("function buildAxes() {").indentMore();
        if (hAxis.exists()) {
            out.onNewLine().add("axes.select('g.axis.x').call(axis_bottom)");
            if (hAxis.rotatedTicks) addRotateTicks();
            out.endStatement();
        }

        if (vAxis.exists()) {
            out.onNewLine().add("axes.select('g.axis.y').call(axis_left)");
            if (vAxis.rotatedTicks) addRotateTicks();
            out.endStatement();
        }
        out.indentLess().add("}").endStatement().ln();
    }

    public List<Object> getCategories(Field[] ff) {
        Set<Object> all = new LinkedHashSet<Object>();
        for (Field f : ff) if (f.preferCategorical()) Collections.addAll(all, f.categories());
        return new ArrayList<Object>(all);
    }

    public String getXExtent() {
        return coords == VisTypes.Coordinates.polar ? "geom.inner_radius" : "geom.inner_width";
    }

    public String getYExtent() {
        return coords == VisTypes.Coordinates.polar ? "Math.PI*2" : "geom.inner_height";
    }

    public double[] marginsTLBR() {
        return this.marginTLBR;
    }

    public void writeAestheticScales(VisSingle vis) {
        Param color = getColor(vis);
        Param[] size = getSize(vis);
        if (color == null && size.length == 0) return;

        out.onNewLine().comment("Aesthetic Functions");
        if (color != null) {
            addColorScale(color, vis);
            out.onNewLine().add("var color = function(d) { return scale_color(" + D3Util.writeCall(fieldById(color, vis)) + ") }").endStatement();
        }
        if (size.length == 1) {
            // We have exactly one field and util that for the single size scale, with a root transform by default
            addSizeScale("size", size[0], vis, "sqrt");
            out.onNewLine().add("var size = function(d) { return scale_size(" + D3Util.writeCall(fieldById(size[0], vis)) + ") }").endStatement();
        } else if (size.length > 1) {
            // We have two field and util them for height and width
            addSizeScale("width", size[0], vis, "linear");
            addSizeScale("height", size[1], vis, "linear");
            out.onNewLine().add("var width = function(d) { return scale_width(" + D3Util.writeCall(fieldById(size[0], vis)) + ") }").endStatement();
            out.onNewLine().add("var height = function(d) { return scale_height(" + D3Util.writeCall(fieldById(size[1], vis)) + ") }").endStatement();
        }
    }

    private Field fieldById(Param p, VisSingle vis) {
        return fieldById(p.asField(), vis);
    }

    public void writeAxes() {
        if (isDiagram) return;                          // No axes needed for diagrams

        // Calculate geometry
        String width = "geom.inner_width";
        String height = "geom.inner_height";
        if (coords == VisTypes.Coordinates.transposed) {
            // They needs swapping
            String t = width;
            width = height;
            height = t;
        }

        // Define the groups for the axes and add titles
        if (hAxis.exists()) {
            out.onNewLine().add("axes.append('g').attr('class', 'x axis')")
                    .addChained("attr('transform','translate(0,' + " + height + " + ')')")
                    .endStatement();
            if (hAxis.title != null) {
                // Add the title centered at the bottom
                out.add("axes.select('g.axis.x').append('text').attr('class', 'title')")
                        .addChained("attr('text-anchor', 'middle')")
                        .addChained("attr('x', " + width + "/2)")
                        .addChained("attr('y', geom.margin_bottom - 6)")
                        .addChained("text(" + Data.quote(hAxis.title) + ")")
                        .endStatement();
            }
        }
        if (vAxis.exists()) {
            out.onNewLine().add("axes.append('g').attr('class', 'y axis')")
                    .addChained("attr('transform','translate(geom.chart_left, 0)')")
                    .endStatement();
            if (vAxis.title != null) {
                // Add the title
                out.add("axes.select('g.axis.y').append('text').attr('class', 'title')")
                        .addChained("attr('text-anchor', 'middle')")
                        .addChained("attr('x', -" + height + "/2)")
                        .addChained("attr('y', 6-geom.inner_left).attr('dy', '0.7em').attr('transform', 'rotate(270)')")
                        .addChained("text(" + Data.quote(vAxis.title) + ")")
                        .endStatement();
            }

        }

        // Define the axes themselves and the method to build (and re-build) them
        out.onNewLine().ln();
        if (hAxis.exists()) {
            out.add("var axis_bottom = d3.svg.axis().scale(" + hAxis.scale + ").innerTickSize(3).outerTickSize(0)");
            if (hAxis.tickValues != null)
                out.addChained("tickValues([").addQuoted(hAxis.tickValues).add("])");

            if (hAxis.isLog()) out.addChained("ticks(7, ',.g3')");
            out.endStatement();
        }
        if (vAxis.exists()) {
            out.add("var axis_left = d3.svg.axis().orient('left').scale(" + vAxis.scale + ").innerTickSize(3).outerTickSize(0)");
            if (vAxis.tickValues != null)
                out.addChained("tickValues([").addQuoted(vAxis.tickValues).add("])");
            if (vAxis.isLog()) out.addChained("ticks(7, ',.g3')");
            out.endStatement();
        }
        defineAxesBuild();
    }

    public void writeCoordinateScales(D3Interaction interaction) {
        writePositionScale("x", positionFields.allXFields, getXRange(), allElementsLikeFillingToEdge());
        writePositionScale("y", positionFields.allYFields, getYRange(), false);
        interaction.addScaleInteractivity();
    }

    public void writeLegends(VisSingle vis) {
        if (vis.fColor.isEmpty() || colorLegendField == null) return;
        if (!vis.fColor.get(0).equals(colorLegendField.name)) return;
        String legendTicks;
        if (colorLegendField.preferCategorical()) {
            // Categorical data can just grab it from the domain
            legendTicks = "scale_color.domain()";
        } else {
            // Numeric must calculate a nice range
            NumericScale details = Auto.makeNumericScale(colorLegendField, true, 0, 0.25, 7, false);
            Double[] divisions = details.divisions;
            if (details.granular) {
                // Granular data has divisions BETWEEN the values, not at them, so need to fix that
                Double[] newDiv = new Double[divisions.length - 1];
                for (int i = 0; i < newDiv.length; i++) newDiv[i] = (divisions[i] + divisions[i + 1]) / 2;
                divisions = newDiv;
            }
            // Reverse
            for (int i = 0; i < divisions.length / 2; i++) {
                Double t = divisions[divisions.length - 1 - i];
                divisions[divisions.length - 1 - i] = divisions[i];
                divisions[i] = t;
            }
            legendTicks = "[" + Data.join(divisions) + "]";
        }

        String title = colorLegendField.label;
        if (title == null) title = colorLegendField.name;

        out.add("BrunelD3.addLegend(legends, " + out.quote(title) + ", scale_color, " + legendTicks + ")").endStatement();
    }

    private void addColorScale(Param p, VisSingle vis) {
        Field f = fieldById(p, vis);
        scaleWithDomain("color", new Field[]{f}, Purpose.color, 9, "linear");
        Object[] palette = Palette.makePalette(f, p.hasModifiers() ? p.firstModifier().asString() : null);
        out.addChained("range([ ").addQuoted(palette).add("])").endStatement();
    }

    private void addRotateTicks() {
        out.add(".selectAll('.tick text')")
                .addChained("style('text-anchor', 'end')")
                .addChained("attr('dx', '-.3em')")
                .addChained("attr('dy', '.6em')")
                .addChained("attr('transform', function(d) { return 'rotate(-45)' })");
    }

    private void addSizeScale(String name, Param p, VisSingle vis, String defaultTransform) {
        double max = p.hasModifiers() ? p.firstModifier().asDouble() / 100 : 1.0;
        Field f = fieldById(p, vis);

        scaleWithDomain(name, new Field[]{f}, Purpose.size, 2, defaultTransform);
        if (f.preferCategorical()) {
            int length = f.categories().length;
            double[] sizes = new double[length];
            for (int i = 0; i < length; i++) sizes[i] = max * (i + 1.0) / length;
            out.addChained("range(" + Arrays.toString(sizes) + ")");
        } else {
            out.addChained("range([0.05, " + max + "])");
        }
        out.endStatement();
    }

    private boolean allElementsLikeFillingToEdge() {
        // If any element likes padding, it wins
        for (VisSingle e : element) if (!e.tElement.producesSingleShape) return false;
        return true;
    }

    private boolean chooseIsDiagram() {
        // Any non-diagram make the chart all non-diagram. Mixing diagrams and non-diagrams will
        // likely be useless at best, but we will not throw an error for it
        for (VisSingle e : element) if (e.tDiagram == null) return false;
        return true;
    }

    private Field combineNumericFields(Field[] ff) {
        List<Object> data = new ArrayList<Object>();
        for (Field f : ff)
            for (int i = 0; i < f.rowCount(); i++) {
                Object value = f.value(i);
                if (value instanceof Range) {
                    data.add(((Range) value).low);
                    data.add(((Range) value).high);
                }
                data.add(Data.asNumeric(value));
            }
        return Data.makeColumnField("combined", null, data.toArray(new Object[data.size()]));
    }

    private Field fieldById(String fieldName, VisSingle vis) {
        for (int i = 0; i < element.length; i++) {
            if (element[i] == vis) {
                Field field = elementData[i].field(fieldName);
                if (field == null) throw new IllegalStateException("Unknown field " + fieldName);
                return field;
            }
        }
        throw new IllegalStateException("Passed in a vis that was not part of the system defined in the constructor");
    }

    private Param getColor(VisSingle vis) {
        return vis.fColor.isEmpty() ? null : vis.fColor.get(0);
    }

    private Field getColorLegendField() {
        Field result = null;
        for (VisSingle vis : element) {
            boolean auto = vis.tLegends == VisTypes.Legends.auto;
            if (vis.fColor.isEmpty()) continue;                             // No color means no color legend
            if (vis.tLegends == VisTypes.Legends.none) continue;            // No legend if not asked for one

            Field f = fieldById(getColor(vis).asField(), vis);
            if (auto && f.name.equals("#selection")) continue;              // No default legend for selection

            if (result == null) result = f;                                 // The first color definition
            else if (!same(result, f)) return null;                         // Two incompatible colors
        }
        return result;
    }

    private double getIncludeZeroFraction(Field[] fields, Purpose purpose) {
        if (purpose == Purpose.x) return 0.1;               // Really do not want much empty space on color axes
        if (purpose == Purpose.size) return 0.9;            // Almost always want to go to zero
        if (purpose == Purpose.color) return 0.2;           // Color

        // For 'Y'

        // If any position are  counts or sums, always include zero
        for (Field f : fields)
            if (f.name.equals("#count") || "sum".equals(f.getStringProperty("summary"))) return 1.0;

        // Really want it for bar/area charts that are not ranges
        for (VisSingle e : element)
            if ((e.tElement == VisTypes.Element.bar || e.tElement == VisTypes.Element.area)
                    && e.fRange == null) return 0.8;

        // By default, only if we have 20% extra space
        return 0.2;
    }

    private double getNumericPaddingFraction(Purpose purpose) {
        if (purpose == Purpose.color || purpose == Purpose.size) return 0;
        double pad = 0;
        for (VisSingle e : element) {
            if (e.tElement == VisTypes.Element.point || e.tElement == VisTypes.Element.polygon
                    || e.tElement == VisTypes.Element.edge || e.tElement == VisTypes.Element.path)
                pad = Math.max(pad, 0.02);
            else if (e.tElement == VisTypes.Element.text)
                pad = Math.max(pad, 0.1);
            else if (e.tElement == VisTypes.Element.bar && purpose != Purpose.y)
                pad = Math.max(pad, 0.02);

        }
        return pad;
    }

    /**
     * Returns position for the sizes as an array
     */
    private Param[] getSize(VisSingle vis) {
        List<Param> fSize = vis.fSize;
        return fSize.toArray(new Param[fSize.size()]);
    }

    private String getXRange() {
        if (coords == VisTypes.Coordinates.polar) return "[0, geom.inner_radius]";
        boolean reversed = coords == VisTypes.Coordinates.transposed && positionFields.xCategorical;
        return reversed ? "[geom.inner_width,0]" : "[0, geom.inner_width]";
    }

    private String getYRange() {
        if (coords == VisTypes.Coordinates.polar) return "[0, Math.PI*2]";
        boolean reversed = false;

        // If we are on the vertical axis and all the position  are numeric, but the lowest at the start, not the end
        // This means that vertical numeric axes run bottom-to-top, as expected.
        if (coords != VisTypes.Coordinates.transposed) reversed = !positionFields.yCategorical;
        return reversed ? "[geom.inner_height,0]" : "[0, geom.inner_height]";
    }

    private int legendWidth() {
        if (colorLegendField == null) return 0;
        int spaceNeededForTicks = 32 + AxisDetails.maxCategoryWidth(colorLegendField);
        int spaceNeededForTitle = colorLegendField.label.length() * 7;                // Assume 7 pixels per character
        return Math.max(spaceNeededForTicks, spaceNeededForTitle);
    }

    private VisTypes.Axes makeCombinedAxes() {
        // The rule here is that we add axes as much as possible, so presence overrides lack of presence
        VisTypes.Axes result = VisTypes.Axes.auto;
        for (VisSingle e : element) {
            if (e.tAxes == VisTypes.Axes.all) {
                // All means we are done, just return
                return VisTypes.Axes.all;
            } else if (result == VisTypes.Axes.auto) {
                // Override with whatever this is
                result = e.tAxes;
            } else if (e.tAxes == VisTypes.Axes.x) {
                // We need 'x'
                if (result == VisTypes.Axes.y) return VisTypes.Axes.all;        // X and Y means all -- done
                else result = VisTypes.Axes.x;
            } else if (e.tAxes == VisTypes.Axes.y) {
                // We need 'y'
                if (result == VisTypes.Axes.x) return VisTypes.Axes.all;        // X and Y means all -- done
                else result = VisTypes.Axes.y;
            }
        }

        // If auto, check for the coordinate system / diagram to determine what is wanted
        if (result == VisTypes.Axes.auto)
            return coords == VisTypes.Coordinates.polar || isDiagram ? VisTypes.Axes.none : VisTypes.Axes.all;
        else
            return result;
    }

    private VisTypes.Coordinates makeCombinedCoords() {
        // For diagrams, we set the coords to polar for the chord chart and clouds
        if (isDiagram)
            for (VisSingle e : element)
                if (e.tDiagram == VisTypes.Diagram.chord || e.tDiagram == VisTypes.Diagram.cloud)
                    return VisTypes.Coordinates.polar;

        // The rule here is that we return the one with the highest ordinal value;
        // that will correspond to the most "unusual". In practice this means that
        // you need only define 'polar' or 'transpose' in one chart
        VisTypes.Coordinates result = element[0].coords;
        for (VisSingle e : element) if (e.coords.compareTo(result) > 0) result = e.coords;

        return result;
    }

    // Determine if position are the same
    private boolean same(Field a, Field b) {
        return a.name.equals(b.name) && a.preferCategorical() == b.preferCategorical();
    }

    private int scaleWithDomain(String name, Field[] fields, Purpose purpose, int numericDomainDivs, String defaultTransform) {
        out.onNewLine().add("var", "scale_" + name, "= ");

        // No position for this dimension, so util a default [0,1] scale
        if (fields.length == 0) {
            out.add("d3.scale.linear().domain([0,1])");
            return -1;
        }

        Field field = fields[0];

        // Categorical field (includes binned data)
        if (ModelUtil.combinationIsCategorical(fields)) {
            // Combine all categories in the position after each color
            List<Object> list = getCategories(fields);
            out.add("d3.scale.ordinal()").addChained("domain([").addQuotedCollection(list).add("])");
            return list.size();
        }

        // Determine how much we want to include zero
        double includeZero = getIncludeZeroFraction(fields, purpose);

        // Build a combined scale field and force the desired transform on it for x and y dimensions
        Field scaleField = fields.length == 1 ? field : combineNumericFields(fields);
        if (name.equals("x")) {
            // We need to copy it as we are modifying it
            if (scaleField == field) scaleField = field.rename(field.name, field.label);
            scaleField.setProperty("transform", positionFields.xTransform);
        }
        if (name.equals("y")) {
            // We need to copy it as we are modifying it
            if (scaleField == field) scaleField = field.rename(field.name, field.label);
            scaleField.setProperty("transform", positionFields.yTransform);
        }

        // We util a nice scale only for rectangular coordinates
        boolean nice = (name.equals("x") || name.equals("y")) && coords != VisTypes.Coordinates.polar;
        double padding = getNumericPaddingFraction(purpose);

        NumericScale detail = Auto.makeNumericScale(scaleField, nice, padding, includeZero, 9, false);
        double min = detail.min;
        double max = detail.max;

        Object[] divs = new Object[numericDomainDivs];
        if (field.hasProperty("date")) {
            DateFormat dateFormat = (DateFormat) field.getProperty("dateFormat");
            D3Util.DateBuilder dateBuilder = new D3Util.DateBuilder();
            for (int i = 0; i < divs.length; i++) {
                double v = min + (max - min) * i / (numericDomainDivs - 1);
                divs[i] = dateBuilder.make(Data.asDate(v), dateFormat);
            }
            out.add("d3.time.scale()");
        } else {
            // If requested to have a specific transform, util that. Otherwise util the one the field suggests.
            // Some scales (like for an area size) have a default transform (e.g. root) and we
            // util that if the field wants a linear scale.
            String transform = null;
            if (name.equals("x")) transform = positionFields.xTransform;
            if (name.equals("y")) transform = positionFields.yTransform;
            if (transform == null) {
                // We are free to choose -- the user did not specify
                transform = (String) scaleField.getProperty("transform");
                if (transform == null) transform = "linear";
                if (transform.equals("linear")) transform = defaultTransform;
            }
            if (transform.equals("root")) transform = "sqrt";                   // D3 uses this name
            out.add("d3.scale." + transform + "()");
            for (int i = 0; i < divs.length; i++) divs[i] = min + (max - min) * i / (numericDomainDivs - 1);
        }
        out.addChained("domain([").add(Data.join(divs)).add("])");
        return -1;
    }

    private void writePositionScale(String name, Field[] fields, String range, boolean fillToEdge) {
        int categories = scaleWithDomain(name, fields, Purpose.valueOf(name), 2, "linear");

        if (fields.length == 0) {
            out.addChained("range(" + range + ")");
        } else if (categories > 0) {
            // Lines and areas should go all the way to the edge
            if (fillToEdge)
                out.addChained("rangePoints(" + range + ", 0)");
            else
                out.addChained("rangePoints(" + range + ", 1)");
        } else {
            out.addChained("range(" + range + ")");
        }
        out.endStatement();
    }

}
