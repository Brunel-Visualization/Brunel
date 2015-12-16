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

import org.brunel.action.Param;
import org.brunel.build.chart.ChartCoordinates;
import org.brunel.build.chart.ChartStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.color.ColorMapping;
import org.brunel.color.Palette;
import org.brunel.data.Data;
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
 *
 * IMPORTANT NOTE:
 *
 * The terms 'x' and 'y' both apply to the theoretical location. Whe we transpose a chart we move
 * axes and dimensions around, so we cannot always say 'x' runs horizontally. So in the code below
 * 'x' and 'y' are used only for the untransformed locations. We use left, right, top, bottom, h and v
 * for the transformed ones
 */
public class D3ScaleBuilder {

    final VisTypes.Coordinates coords;                      // Combined coordinate system derived from all elements
    final VisTypes.Diagram diagram;                         // The first diagram for this system (null for coords)
    private final Field colorLegendField;                   // Field to use for the color legend
    private final AxisDetails hAxis, vAxis;                   // The same as the above, but at the physical location
    private final double[] marginTLBR;                      // Margins between the coordinate area and the chart space
    private final ChartStructure structure;                 // Overall detail on the chart composition
    private final VisSingle[] elements;                     // The elements that define the scales used
    private final ScriptWriter out;                         // Write definitions to here
    public D3ScaleBuilder(ChartStructure structure, double chartWidth, double chartHeight, ScriptWriter out) {
        this.structure = structure;
        this.elements = structure.elements;
        this.out = out;
        this.diagram = findDiagram();
        this.coords = makeCombinedCoords();
        VisTypes.Axes axes = makeCombinedAxes();

        // Create the position needed
        this.colorLegendField = getColorLegendField();

        ChartCoordinates coords = structure.coordinates;

        // Set the axis information for each dimension
        AxisDetails xAxis, yAxis;
        if (axes == VisTypes.Axes.all || axes == VisTypes.Axes.x) {
            xAxis = new AxisDetails("x", coords.allXFields, coords.xCategorical);
        } else
            xAxis = new AxisDetails("x", new Field[0], coords.xCategorical);
        if (axes == VisTypes.Axes.all || axes == VisTypes.Axes.y)
            yAxis = new AxisDetails("y", coords.allYFields, coords.yCategorical);
        else
            yAxis = new AxisDetails("y", new Field[0], coords.yCategorical);

        // Map the dimension to the physical location on screen
        if (this.coords == VisTypes.Coordinates.transposed) {
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
        hAxis.layoutHorizontally(chartWidth - vAxis.size - legendWidth, elementsFillHorizontal());

        // Set the margins
        int marginTop = vAxis.topGutter;                                    // Only the vAxis needs space here
        int marginLeft = Math.max(vAxis.size, hAxis.leftGutter);            // Width of vAxis, or horizontal gutter
        int marginBottom = Math.max(hAxis.size, vAxis.bottomGutter);        // Height of hAxis, or gutter for vAxis
        int marginRight = Math.max(hAxis.rightGutter, legendWidth);         // Overflow for hAxis, or legend
        marginTLBR = new double[]{marginTop, marginLeft, marginBottom, marginRight};
    }

    private VisTypes.Diagram findDiagram() {
        // Any diagram make the chart all diagram. Mixing diagrams and non-diagrams will
        // likely be useless at best, but we will not throw an error for it
        for (VisSingle e : elements) if (e.tDiagram != null) return e.tDiagram;
        return null;
    }

    private VisTypes.Coordinates makeCombinedCoords() {
        // For diagrams, we set the coords to polar for the chord chart and clouds, and centered for networks
        if (diagram == VisTypes.Diagram.chord || diagram == VisTypes.Diagram.cloud)
            return VisTypes.Coordinates.polar;

        // The rule here is that we return the one with the highest ordinal value;
        // that will correspond to the most "unusual". In practice this means that
        // you need only define 'polar' or 'transpose' in one chart
        VisTypes.Coordinates result = elements[0].coords;
        for (VisSingle e : elements) if (e.coords.compareTo(result) > 0) result = e.coords;

        return result;
    }

    private VisTypes.Axes makeCombinedAxes() {
        // The rule here is that we add axes as much as possible, so presence overrides lack of presence
        VisTypes.Axes result = VisTypes.Axes.auto;
        for (VisSingle e : elements) {
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
            return coords == VisTypes.Coordinates.polar || diagram != null ? VisTypes.Axes.none : VisTypes.Axes.all;
        else
            return result;
    }

    private Field getColorLegendField() {
        Field result = null;
        for (VisSingle vis : elements) {
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

    private int legendWidth() {
        if (colorLegendField == null) return 0;
        AxisDetails legendAxis = new AxisDetails("color", new Field[]{colorLegendField}, colorLegendField.preferCategorical());
        int spaceNeededForTicks = 32 + legendAxis.maxCategoryWidth();
        int spaceNeededForTitle = colorLegendField.label.length() * 7;                // Assume 7 pixels per character
        return 6 + Math.max(spaceNeededForTicks, spaceNeededForTitle);                // Add some spacing
    }

    private boolean elementsFillHorizontal() {
        boolean fillToEdge = true;
        for (VisSingle e : elements)
            if (e.tElement != VisTypes.Element.line && e.tElement != VisTypes.Element.area) fillToEdge = false;
        return fillToEdge;
    }

    private Field fieldById(String fieldName, VisSingle vis) {
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] == vis) {
                Field field = structure.data[i].field(fieldName);
                if (field == null) throw new IllegalStateException("Unknown field " + fieldName);
                return field;
            }
        }
        throw new IllegalStateException("Passed in a vis that was not part of the system defined in the constructor");
    }

    private Param getColor(VisSingle vis) {
        return vis.fColor.isEmpty() ? null : vis.fColor.get(0);
    }

    // Determine if position are the same
    private boolean same(Field a, Field b) {
        return a.name.equals(b.name) && a.preferCategorical() == b.preferCategorical();
    }

    public boolean allNumeric(Field[] fields) {
        for (Field f : fields)
            if (!f.isNumeric())
                return false;
        return true;
    }

    public Double getGranularitySuitableForSizing(Field[] ff) {
        Double r = null;
        for (Field f : ff) {
            if (f.isDate()) continue;   // No date granularity use
            Double g = f.numericProperty("granularity");
            if (g != null && g / (f.max() - f.min()) > 0.02) {
                if (r == null || g < r) r = g;
            }
        }
        return r;
    }

    public double[] marginsTLBR() {
        return this.marginTLBR;
    }

    public boolean needsAxes() {
        return hAxis.exists() || vAxis.exists();
    }

    public void writeAestheticScales(VisSingle vis) {
        Param color = getColor(vis);
        Param[] size = getSize(vis);
        Param opacity = getOpacity(vis);
        if (color == null && opacity == null && size.length == 0) return;

        out.onNewLine().comment("Aesthetic Functions");
        if (color != null) {
            addColorScale(color, vis);
            out.onNewLine().add("var color = function(d) { return scale_color(" + D3Util.writeCall(fieldById(color, vis)) + ") }").endStatement();
        }
        if (opacity != null) {
            addOpacityScale(opacity, vis);
            out.onNewLine().add("var opacity = function(d) { return scale_opacity(" + D3Util.writeCall(fieldById(opacity, vis)) + ") }").endStatement();
        }
        if (size.length == 1) {
            // We have exactly one field and util that for the single size scale, with a root transform by default for point elements
            String defaultTransform = vis.tElement == VisTypes.Element.point ? "sqrt" : "linear";
            addSizeScale("size", size[0], vis, defaultTransform);
            out.onNewLine().add("var size = function(d) { return scale_size(" + D3Util.writeCall(fieldById(size[0], vis)) + ") }").endStatement();
        } else if (size.length > 1) {
            // We have two field and util them for height and width
            addSizeScale("width", size[0], vis, "linear");
            addSizeScale("height", size[1], vis, "linear");
            out.onNewLine().add("var width = function(d) { return scale_width(" + D3Util.writeCall(fieldById(size[0], vis)) + ") }").endStatement();
            out.onNewLine().add("var height = function(d) { return scale_height(" + D3Util.writeCall(fieldById(size[1], vis)) + ") }").endStatement();
        }
    }

    public void writeAxes() {
        if (diagram != null) return;                          // No axes needed for diagrams

        // Calculate geom
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
                        .addChained("attr('y', geom.inner_bottom - 6)")
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

    private void addRotateTicks() {
        out.add(".selectAll('.tick text')")
                .addChained("style('text-anchor', 'end')")
                .addChained("attr('dx', '-.3em')")
                .addChained("attr('dy', '.6em')")
                .addChained("attr('transform', function(d) { return 'rotate(-45)' })");
    }

    public void writeCoordinateScales(D3Interaction interaction) {
        writePositionScale("x", structure.coordinates.allXFields, getXRange(), elementsFillHorizontal());
        writePositionScale("y", structure.coordinates.allYFields, getYRange(), false);
        interaction.addScaleInteractivity();
    }

    private void writePositionScale(String name, Field[] fields, String range, boolean fillToEdge) {
        int categories = scaleWithDomain(name, fields, Purpose.valueOf(name), 2, "linear", null);

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

    private String getXRange() {
        if (coords == VisTypes.Coordinates.polar) return "[0, geom.inner_radius]";

        boolean reversed = coords == VisTypes.Coordinates.transposed && structure.coordinates.xCategorical;
        return reversed ? "[geom.inner_width,0]" : "[0, geom.inner_width]";
    }

    private String getYRange() {
        if (coords == VisTypes.Coordinates.polar) return "[0, Math.PI*2]";

        boolean reversed = false;
        // If we are on the vertical axis and all the position  are numeric, but the lowest at the start, not the end
        // This means that vertical numeric axes run bottom-to-top, as expected.
        if (coords != VisTypes.Coordinates.transposed) reversed = !structure.coordinates.yCategorical;
        return reversed ? "[geom.inner_height,0]" : "[0, geom.inner_height]";
    }

    private int scaleWithDomain(String name, Field[] fields, Purpose purpose, int numericDomainDivs, String defaultTransform, Object[] partitionPoints) {

        out.onNewLine().add("var", "scale_" + name, "= ");

        // No position for this dimension, so util a default [0,1] scale
        if (fields.length == 0) {
            out.add("d3.scale.linear().domain([0,1])");
            return -1;
        }

        Field field = fields[0];

        // Categorical field (includes binned data)
        if (ModelUtil.combinationIsCategorical(fields, purpose.isCoord)) {
            // Combine all categories in the position after each color
            // We use all the categories in the data; we do not need the partition points
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
            scaleField.set("transform", structure.coordinates.xTransform);
        }
        if (name.equals("y")) {
            // We need to copy it as we are modifying it
            if (scaleField == field) scaleField = field.rename(field.name, field.label);
            scaleField.set("transform", structure.coordinates.yTransform);
        }

        // We util a nice scale only for rectangular coordinates
        boolean nice = (name.equals("x") || name.equals("y")) && coords != VisTypes.Coordinates.polar;
        double[] padding = getNumericPaddingFraction(purpose, coords);

        // Areas and line should fill the horizontal dimension, as should any binned field
        if (scaleField.isBinned() || purpose == Purpose.x && elementsFillHorizontal()) {
            nice = false;
            padding = new double[]{0, 0};
            includeZero = 0;
        }

        NumericScale detail = Auto.makeNumericScale(scaleField, nice, padding, includeZero, 9, false);
        double min = detail.min;
        double max = detail.max;

        Object[] divs = new Object[numericDomainDivs];
        if (field.isDate()) {
            DateFormat dateFormat = (DateFormat) field.property("dateFormat");
            D3Util.DateBuilder dateBuilder = new D3Util.DateBuilder();
            for (int i = 0; i < divs.length; i++) {
                Object v;
                if (partitionPoints == null)
                    v = min + (max - min) * i / (numericDomainDivs - 1);
                else
                    v = partitionPoints[i];
                divs[i] = dateBuilder.make(Data.asDate(v), dateFormat, true);
            }
            out.add("d3.time.scale()");
        } else {
            // If requested to have a specific transform, util that. Otherwise util the one the field suggests.
            // Some scales (like for an area size) have a default transform (e.g. root) and we
            // util that if the field wants a linear scale.
            String transform = null;
            if (name.equals("x")) transform = structure.coordinates.xTransform;
            if (name.equals("y")) transform = structure.coordinates.yTransform;

            // Size must not get a transform as it will seriously distort things
            if (purpose == Purpose.size) transform = defaultTransform;
            else if (transform == null) {
                // We are free to choose -- the user did not specify
                transform = (String) scaleField.property("transform");
                if (transform == null) transform = "linear";
                if (transform.equals("linear")) transform = defaultTransform;
            }
            if (transform.equals("root")) transform = "sqrt";                   // D3 uses this name
            out.add("d3.scale." + transform + "()");
            for (int i = 0; i < divs.length; i++) {
                if (partitionPoints == null)
                    divs[i] = min + (max - min) * i / (numericDomainDivs - 1);
                else
                    divs[i] = partitionPoints[i];
            }
        }
        out.addChained("domain([").add(Data.join(divs)).add("])");
        return -1;
    }

    public List<Object> getCategories(Field[] ff) {
        Set<Object> all = new LinkedHashSet<Object>();
        for (Field f : ff) if (f.preferCategorical()) Collections.addAll(all, f.categories());
        return new ArrayList<Object>(all);
    }

    private double getIncludeZeroFraction(Field[] fields, Purpose purpose) {

        if (purpose == Purpose.x) return 0.1;               // Really do not want much empty space on color axes
        if (purpose == Purpose.size) return 0.9;            // Almost always want to go to zero
        if (purpose == Purpose.color) return 0.2;           // Color

        // For 'Y'

        // If any position are  counts or sums, always include zero
        for (Field f : fields)
            if (f.name.equals("#count") || "sum".equals(f.stringProperty("summary"))) return 1.0;

        // Really want it for bar/area charts that are not ranges
        for (VisSingle e : elements)
            if ((e.tElement == VisTypes.Element.bar || e.tElement == VisTypes.Element.area)
                    && e.fRange == null) return 0.8;

        // By default, only if we have 20% extra space
        return 0.2;
    }

    private Field combineNumericFields(Field[] ff) {
        List<Object> data = new ArrayList<Object>();
        for (Field f : ff)
            for (int i = 0; i < f.rowCount(); i++) {
                Object value = f.value(i);
                if (value instanceof Range) {
                    data.add(Data.asNumeric(((Range) value).low));
                    data.add(Data.asNumeric(((Range) value).high));
                } else
                    data.add(Data.asNumeric(value));
            }
        Field combined = Data.makeColumnField("combined", null, data.toArray(new Object[data.size()]));
        combined.set("numeric", true);
        return combined;
    }

    private double[] getNumericPaddingFraction(Purpose purpose, VisTypes.Coordinates coords) {
        double[] padding = new double[]{0, 0};
        if (purpose == Purpose.color || purpose == Purpose.size) return padding;                // None for aesthetics
        if (coords == VisTypes.Coordinates.polar) return padding;                               // None for polar angle
        for (VisSingle e : elements) {
            boolean noBottomYPadding = e.tElement == VisTypes.Element.bar || e.tElement == VisTypes.Element.area || e.tElement == VisTypes.Element.line;
            if (e.tElement == VisTypes.Element.text) {
                // Text needs lot of padding
                padding[0] = Math.max(padding[0], 0.1);
                padding[1] = Math.max(padding[1], 0.1);
            } else if (purpose == Purpose.y && noBottomYPadding) {
                // A little padding on the top only
                padding[1] = Math.max(padding[1], 0.02);
            } else {
                // A little padding
                padding[0] = Math.max(padding[0], 0.02);
                padding[1] = Math.max(padding[1], 0.02);
            }
        }
        return padding;
    }

    public void writeLegends(VisSingle vis) {
        if (vis.fColor.isEmpty() || colorLegendField == null) return;
        if (!vis.fColor.get(0).asField().equals(colorLegendField.name)) return;
        String legendTicks, legendLabels = null;
        if (colorLegendField.preferCategorical()) {
            // Categorical data can just grab it from the domain
            legendTicks = "scale_color.domain()";
            // Binned data reads in opposite direction (bottom to top)
            if (colorLegendField.isBinned()) legendTicks += ".reverse()";
        } else {
            // Numeric must calculate a nice range
            NumericScale details = Auto.makeNumericScale(colorLegendField, true, new double[]{0, 0}, 0.25, 7, false);
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

            if (colorLegendField.isDate()) {
                // Convert to dates
                DateFormat dateFormat = (DateFormat) colorLegendField.property("dateFormat");
                D3Util.DateBuilder dateBuilder = new D3Util.DateBuilder();
                String[] divs = new String[divisions.length];
                String[] labels = new String[divisions.length];
                for (int i = 0; i < divs.length; i++) {
                    divs[i] = dateBuilder.make(Data.asDate(divisions[i]), dateFormat, true);
                    labels[i] = "'" + colorLegendField.format(divisions[i]) + "'";
                }
                legendTicks = "[" + Data.join(divs) + "]";
                legendLabels = "[" + Data.join(labels) + "]";
            } else {
                legendTicks = "[" + Data.join(divisions) + "]";
            }
        }

        String title = colorLegendField.label;
        if (title == null) title = colorLegendField.name;

        out.add("BrunelD3.addLegend(legends, " + out.quote(title) + ", scale_color, " + legendTicks);
        if (legendLabels != null) out.add(", ").add(legendLabels);
        out.add(")").endStatement();
    }

    private void addColorScale(Param p, VisSingle vis) {
        Field f = fieldById(p, vis);

        // Determine if the element fills a big area
        boolean largeElement = vis.tElement == VisTypes.Element.area || vis.tElement == VisTypes.Element.bar
                || vis.tElement == VisTypes.Element.polygon;
        if (vis.tDiagram == VisTypes.Diagram.map || vis.tDiagram == VisTypes.Diagram.treemap)
            largeElement = true;

        if (vis.tElement == VisTypes.Element.path && !vis.fSize.isEmpty())
            largeElement = true;

        ColorMapping palette = Palette.makeColorMapping(f, p.modifiers(), largeElement);
        scaleWithDomain("color", new Field[]{f}, Purpose.color, palette.values.length, "linear", palette.values);
        out.addChained("range([ ").addQuoted(palette.colors).add("])").endStatement();
    }

    private void addOpacityScale(Param p, VisSingle vis) {
        double min = p.hasModifiers() ? p.firstModifier().asDouble() : 0.2;
        Field f = fieldById(p, vis);

        scaleWithDomain("opacity", new Field[]{f}, Purpose.color, 2, "linear", null);
        if (f.preferCategorical()) {
            int length = f.categories().length;
            double[] sizes = new double[length];
            // degenerate data gets the min value
            if (length == 1)
                sizes[0] = min;
            else
                for (int i = 0; i < length; i++) sizes[i] = min + (1 - min) * i / (length - 1);
            out.addChained("range(" + Arrays.toString(sizes) + ")");
        } else {
            out.addChained("range([" + min + ", 1])");
        }
        out.endStatement();
    }

    private void addSizeScale(String name, Param p, VisSingle vis, String defaultTransform) {

        Object[] sizes;
        if (p.modifiers().length > 0) {
            sizes = getSizes(p.modifiers()[0].asList());
        } else {
            sizes = new Object[]{0.05, 1.0};
        }

        Field f = fieldById(p, vis);
        Object[] divisions;
        if (f.isNumeric()) {
            // Divide up and interpolate
            divisions = Palette.fieldSplits(f, sizes.length);
        } else {
            // Alternate among categories
            divisions = f.categories();
        }

        scaleWithDomain(name, new Field[]{f}, Purpose.size, sizes.length, defaultTransform, divisions);
        out.addChained("range([ ").add(Data.join(sizes)).add("])").endStatement();
    }

    private Field fieldById(Param p, VisSingle vis) {
        return fieldById(p.asField(), vis);
    }

    private Param getOpacity(VisSingle vis) {
        return vis.fOpacity.isEmpty() ? null : vis.fOpacity.get(0);
    }

    /**
     * Returns position for the sizes as an array
     */
    private Param[] getSize(VisSingle vis) {
        List<Param> fSize = vis.fSize;
        return fSize.toArray(new Param[fSize.size()]);
    }

    private Object[] getSizes(List<Param> params) {
        // The parameters define the lists we want
        List<Double> result = new ArrayList<Double>();
        for (Param p : params) {
            String s = p.asString();
            if (s.endsWith("%")) s = s.substring(0, s.length() - 1);
            Double d = Data.asNumeric(s);
            if (d != null) result.add(d / 100);
        }
        if (result.isEmpty()) return new Object[]{0.05, 1.0};
        if (result.size() == 1) result.add(0, 0.05);
        return result.toArray(new Object[result.size()]);
    }

    /* The purpsoe of a scale */
    private enum Purpose {
        x(true), y(true), size(false), color(false);
        public final boolean isCoord;

        Purpose(boolean isCoord) {
            this.isCoord = isCoord;
        }
    }

}
