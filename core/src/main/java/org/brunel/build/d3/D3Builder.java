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
import org.brunel.build.AbstractBuilder;
import org.brunel.build.controls.Controls;
import org.brunel.build.d3.element.D3ElementBuilder;
import org.brunel.build.d3.titles.ChartTitleBuilder;
import org.brunel.build.data.DataTransformParameters;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.Accessibility;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.VisTypes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/*
        The methods of this class are called as an Abstract Builder to build the chart
 */
public class D3Builder extends AbstractBuilder {

    private static final String COPYRIGHT_COMMENTS = "\t<!--\n" +
            "\t\tD3 Copyright \u00a9 2012, Michael Bostock\n" +
            "\t\tjQuery Copyright \u00a9 2010 by The jQuery Project\n" +
            "\t\tsumoselect Copyright \u00a9 2014 Hemant Negi\n " +
            "\t-->\n";

    /**
     * Return the required builder with default options
     */
    public static D3Builder make() {
        return make(new BuilderOptions());
    }

    /**
     * Return the required builder
     */
    public static D3Builder make(BuilderOptions options) {
        return new D3Builder(options);
    }

    private ScriptWriter out;                   // Where to write code
    public int visWidth, visHeight;             // Overall vis size
    private D3ScaleBuilder scalesBuilder;       // The scales for the current chart
    private D3Interaction interaction;          // Builder for interactions
    private D3ElementBuilder[] elementBuilders; // Builder for each element
    private boolean hasMultipleCharts;          // flag to indicate multiple charts in the same vis

    private D3Builder(BuilderOptions options) {
        super(options);
    }

    public String getVisualization() {
        return out.content();
    }

    public String makeImports() {

        String pattern = "\t<script src=\"%s\" charset=\"utf-8\"></script>\n";

        String base = COPYRIGHT_COMMENTS +
                String.format(pattern, "http://cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min.js")
                + String.format(pattern, "http://cdnjs.cloudflare.com/ajax/libs/topojson/1.6.20/topojson.min.js");

        if (getControls().isNeeded()) {
            base = base + String.format(pattern, "http://code.jquery.com/jquery-1.10.2.js")
                    + String.format(pattern, "http://code.jquery.com/ui/1.11.4/jquery-ui.js");
        }

        if (options.locJavaScript.startsWith("file")) {
            base = base
                    + String.format(pattern, options.locJavaScript + "/BrunelData.js")
                    + String.format(pattern, options.locJavaScript + "/BrunelD3.js");
            if (getControls().isNeeded()) base = base
                    + String.format(pattern, options.locJavaScript + "/BrunelEventHandlers.js")
                    + String.format(pattern, options.locJavaScript + "/BrunelJQueryControlFactory.js")
                    + String.format(pattern, options.locJavaScript + "/sumoselect/jquery.sumoselect.min.js");
        } else {
            base = base + String.format(pattern, options.locJavaScript + "/brunel." + options.version + ".min.js");
            if (getControls().isNeeded())
                base = base + String.format(pattern, options.locJavaScript + "/brunel.controls." + options.version + ".min.js");
        }
        return base;
    }

    protected void defineChart(ChartStructure structure, double[] location) {

        // Calculate the margins for this chart within the overall size
        double[] chartMargins = new double[]{
                location[0] / 100, location[1] / 100, location[2] / 100, location[3] / 100
        };

        // Create the scales and element builders.   This also creates the interaction instance.
        createBuilders(structure, chartMargins);

        // Write the class definition function
        out.titleComment("Define chart #" + structure.chartID(), "in the visualization");
        out.add("charts[" + structure.chartIndex + "] = function(parentNode, filterRows) {").ln();
        out.indentMore();

        double[] margins = scalesBuilder.marginsTLBR();
        ChartTitleBuilder title = new ChartTitleBuilder(structure, "header");
        ChartTitleBuilder sub = new ChartTitleBuilder(structure, "footer");
        margins[0] += title.verticalSpace();
        margins[2] += sub.verticalSpace();
        scalesBuilder.setAdditionalHAxisOffset(sub.verticalSpace());

        out.add("var geom = BrunelD3.geometry(parentNode || vis.node(),", chartMargins, ",", margins, "),")
                .indentMore()
                .onNewLine().add("elements = [];").at(50).comment("Array of elements in this chart")
                .indentLess();

        // Transpose if needed
        if (scalesBuilder.coords == Coordinates.transposed) out.add("geom.transpose()").endStatement();

        // Now build the main groups
        out.titleComment("Define groups for the chart parts");
        writeMainGroups(structure);
        for (D3ElementBuilder builder : elementBuilders) builder.writePerChartDefinitions();

        title.writeContent("chart", out);
        sub.writeContent("chart", out);

        // Diagrams do not need scales; everything else does
        if (structure.diagram == null) {
            out.titleComment("Scales");
            scalesBuilder.writeCoordinateScales(interaction);

            // Define the Axes
            if (scalesBuilder.needsAxes()) {
                out.titleComment("Axes");
                scalesBuilder.writeAxes();
            }
        }

    }

    private void createBuilders(ChartStructure structure, double[] chartMargins) {
        // Define scales
        double chartWidth = visWidth - chartMargins[1] - chartMargins[3];
        double chartHeight = visHeight - chartMargins[0] - chartMargins[2];
        this.scalesBuilder = new D3ScaleBuilder(structure, chartWidth, chartHeight, out);

        interaction = new D3Interaction(structure, scalesBuilder, out);

        ElementStructure[] structures = structure.elementStructure;
        elementBuilders = new D3ElementBuilder[structures.length];
        for (int i = 0; i < structures.length; i++) {
            if (structures[i].vis.tGuides.isEmpty())
                elementBuilders[i] = new D3ElementBuilder(structures[i], out, scalesBuilder, interaction);
            else
                elementBuilders[i] = new GuideBuilder(structures[i], out, scalesBuilder, interaction);

        }
    }

    protected void defineElement(ElementStructure structure) {
        D3ElementBuilder elementBuilder = elementBuilders[structure.index];

        out.titleComment("Define element #" + structure.elementID());
        out.add("elements[" + structure.index + "] = function() {").indentMore();
        out.onNewLine().add("var original, processed,").at(40).comment("data sets passed in and then transformed")
                .indentMore()
                .onNewLine().add("element, data,").at(40).comment("Brunel element information and brunel data")
                .onNewLine().add("selection;").at(40).comment("D3 selection")
                .indentLess();

        // Add data variables used throughout
        addElementGroups(elementBuilder, structure);

        // Data transforms
        int datasetIndex = structure.getBaseDatasetIndex();
        VisSingle vis = structure.vis;
        D3DataBuilder dataBuilder = new D3DataBuilder(vis, out, structure.data, datasetIndex);
        dataBuilder.writeDataManipulation(createResultFields(vis));

        scalesBuilder.writeAestheticScales(vis);
        scalesBuilder.writeLegends(vis);

        elementBuilder.preBuildDefinitions();

        // Main method to make a vis
        out.titleComment("Build element from data");

        out.add("function build(transitionMillis) {").ln().indentMore();
        elementBuilder.generate(structure.index);
        interaction.addHandlers(vis.tInteraction);

        // If a chart is nested within us, build its facets
        Integer index = structure.chart.innerChartIndex;
        if (index != null) {
            String id = ChartStructure.makeChartID(index);
            out.onNewLine().comment("Build the faceted charts within this chart's selection");
            out.add("vis.select('g.chart" + id + "').selectAll('*').remove()").endStatement()
                    .add("BrunelD3.facet(charts[" + index + "], element, transitionMillis)").endStatement();

        }

        out.indentLess().onNewLine().add("}").ln().ln();

        // Expose the methods and variables we want the user to have access to
        addElementExports(vis, dataBuilder, structure);

        out.indentLess().onNewLine().add("}()").endStatement().ln();
    }

    protected void defineVisSystem(VisItem main, int width, int height) {
        this.visWidth = width;
        this.visHeight = height;
        this.out = new ScriptWriter(options);
        this.hasMultipleCharts = main.children() != null && main.children().length > 1;

        // Write the class definition function (and flag to use strict mode)
        out.add("function ", options.className, "(visId) {").ln().indentMore();
        out.add("\"use strict\";").comment("Strict Mode");

        // Add commonly used definitions
        out.add("var datasets = [],").at(50).comment("Array of datasets for the original data");
        out.add("    pre = function(d, i) { return d },").at(50).comment("Default pre-process does nothing");
        out.add("    post = function(d, i) { return d },").at(50).comment("Default post-process does nothing");
        out.add("    transitionTime = 200,").at(50).comment("Transition time for animations");
        out.add("    charts = [],").at(50).comment("The charts in the system");
        out.add("    hasData = function(d) {return d && (d.row != null)},").at(50).comment("Filters to data items");
        out.add("    vis = d3.select('#' + visId).attr('class', 'brunel')").at(60).comment("the SVG container");
    }

    protected void endChart(ChartStructure structure) {
        out.onNewLine().add("function build(time, noData) {").indentMore();

        // This is a bit of hack to get around d3 support for catgeroical scales.
        // we cannot simply set the zooming, so we have to add this work around at the
        // start of the build
        interaction.addCategoricalScaleAdjustment();

        out.onNewLine().add("var first = elements[0].data() == null").endStatement();
        out.add("if (first) time = 0;").comment("No transition for first call");

        // For coordinate system charts, see if axes are needed
        if (scalesBuilder.needsAxes())
            out.onNewLine().add("buildAxes(time)").endStatement();

        // For maps, see if the graticule is needed
        if (structure.geo != null && structure.geo.withGraticule)
            out.onNewLine().add("buildAxes(time)").endStatement();

        Integer[] order = structure.elementBuildOrder();

        out.onNewLine().add("if ((first || time > -1) && !noData)");
        if (order.length > 1) {
            out.add("{").indentMore();
            for (int i : order)
                out.onNewLine().add("elements[" + i + "].makeData();");
            out.indentLess().onNewLine().add("}").ln();
        } else {
            out.add("elements[0].makeData()").endStatement();
        }
        for (int i : order)
            out.onNewLine().add("elements[" + i + "].build(time);");

        for (D3ElementBuilder builder : elementBuilders) builder.writeBuildCommands();

        out.indentLess().onNewLine().add("}").ln();

        out.ln().comment("Expose the following components of the chart");
        out.add("return {").indentMore()
                .onNewLine().add("elements : elements,")
                .onNewLine().add("interior : interior,");

        if (structure.diagram == null) {
            out.onNewLine().add("scales: {x:scale_x, y:scale_y},");
        }

        if (interaction.getZoomType() != D3Interaction.ZoomType.None) {
            out.onNewLine().add("zoom: function(params, time) {")
                    .continueOnNextLine().add("var v = BrunelD3.panzoom(params, zoom);")
                    .continueOnNextLine().add("if (params) build(time > 0 ? time : 0, true);")
                    .continueOnNextLine().add("return v;");
            out.onNewLine().add("},");
        }

        out.onNewLine().add("build : build")
                .indentLess().onNewLine().add("}").endStatement();

        // Finish the chart method
        if (nesting.containsKey(structure.chartIndex)) {
            // For a nested chart we need to build the chart completely each time, so store the FUNCTION
            out.add("}");
        } else {
            // Non-nested charts just get built once, so execute and store the chart as a built OBJECT
            out.add("}()");
        }

        out.indentLess().endStatement().ln();

    }

    protected void endVisSystem(VisItem main) {

        // Define how we set the data into the system
        out.add("function setData(rowData, i) { datasets[i||0] = BrunelD3.makeData(rowData) }").ln();

        // Define the update functions
        if (nesting.isEmpty()) {
            // For no nesting, it's easy
            out.add("function updateAll(time) { charts.forEach(function(x) {x.build(time || 0)}) }").ln();
        } else {
            // For nesting, need a custom update method
            // TODO make work for more than two charts1
            out.add("function updateAll(time) {").indentMore().ln()
                    .add("var t = time || 20").endStatement()
                    .add("charts[0].build(0)").endStatement()
                    .indentLess().add("}").ln();
        }

        // Define visualization functions
        out.add("function buildAll() {").ln().indentMore()
                .add("for (var i=0;i<arguments.length;i++) setData(arguments[i], i)").endStatement()
                .add("updateAll(transitionTime)").endStatement();
        out.indentLess().add("}").ln().ln();

        // Return the important items
        out.add("return {").indentMore().ln()
                .add("dataPreProcess:").at(24).add("function(f) { if (f) pre = f; return pre },").ln()
                .add("dataPostProcess:").at(24).add("function(f) { if (f) post = f; return post },").ln()
                .add("data:").at(24).add("function(d,i) { if (d) setData(d,i); return datasets[i||0] },").ln()
                .add("visId:").at(24).add("visId,").ln()
                .add("build:").at(24).add("buildAll,").ln()
                .add("rebuild:").at(24).add("updateAll,").ln()
                .add("charts:").at(24).add("charts").ln()
                .indentLess().add("}").ln();

        // Finish the outer class definition
        out.indentLess().onNewLine().add("}").ln();

        // Create the initial raw data table
        D3DataBuilder.writeTables(main, out, options);

        // Call the function on the data
        if (options.generateBuildCode) {
            out.titleComment("Call Code to Build the system");
            out.add("var v = new", options.className, "(" + out.quote(options.visIdentifier) + ")").endStatement();

            //Initialize and wire any events that may be needed for controls.
            //This must be done prior to building the visualization so defaults can be set.
            controls.writeEventHandler(out, "v");

            int length = main.getDataSets().length;

            int enterAnimateTime = enterAnimate(main, length);
            if (enterAnimateTime > 0) {
                out.add("BrunelD3.animateBuild(v,", String.format(options.dataName, 1),
                        ",", enterAnimateTime, ")").endStatement();
            } else {
                out.add("v.build(");
                for (int i = 0; i < length; i++) {
                    if (i > 0) out.add(", ");
                    out.add(String.format(options.dataName, i + 1));
                }
                out.add(")").endStatement();
            }
        }

        // Add controls code
        controls.writeControls(out, "v");

    }

    private int enterAnimate(VisItem main, int dataSetCount) {
        if (dataSetCount != 1) return -1;                           // Need a single data set to animate over

        if (main.children() != null) {
            // Check children
            for (VisItem child : main.children()) {
                int v = enterAnimate(child, dataSetCount);
                if (v >= 0) return v;
            }
        }

        List<Param> effects = main.getSingle().getSingle().fEffects;
        for (Param p : effects) {
            if (p.type() == Param.Type.option && p.asString().equals("enter")) {
                if (p.hasModifiers()) {
                    try {
                        return (int) p.modifiers()[0].asDouble();
                    } catch (Exception ignored) {
                        // fall through to default case
                    }
                }
                return 700;
            }
        }
        return -1;
    }

    private void addElementGroups(D3ElementBuilder builder, ElementStructure structure) {
        String elementTransform = makeElementTransform(scalesBuilder.coords);
        out.add("var elementGroup = interior.append('g').attr('class', 'element" + structure.elementID() + "')");
        Accessibility.addElementInformation(structure, out);
        if (elementTransform != null) out.addChained(elementTransform);
        if (builder.needsDiagramExtras())
            out.continueOnNextLine(",").add("diagramExtras = elementGroup.append('g').attr('class', 'extras')");
        out.continueOnNextLine(",").add("main = elementGroup.append('g').attr('class', 'main')");
        if (builder.needsDiagramLabels())
            out.continueOnNextLine(",")
                    .add("diagramLabels = BrunelD3.undoTransform(elementGroup.append('g').attr('class', 'diagram labels').attr('aria-hidden', 'true'), elementGroup)");

        out.continueOnNextLine(",")
                .add("labels = BrunelD3.undoTransform(elementGroup.append('g').attr('class', 'labels').attr('aria-hidden', 'true'), elementGroup)").endStatement();
    }

    /*
        Builds a mapping from the fields we will use in the built data object to an indexing 0,1,2,3, ...
     */
    private Map<String, Integer> createResultFields(VisSingle vis) {
        LinkedHashSet<String> needed = new LinkedHashSet<>();
        if (vis.fY.size() > 1) {
            // A series needs special handling -- Y's are different in output than input
            if (vis.stacked) {
                // Stacked series chart needs lower and upper values
                needed.add("#values$lower");
                needed.add("#values$upper");
            }
            // Always need series and values
            needed.add("#series");
            needed.add("#values");

            // And then all the X fields
            for (Param p : vis.fX) needed.add(p.asField());

            // And the non-position fields
            Collections.addAll(needed, vis.nonPositionFields());
        } else {
            if (vis.stacked) {
                // Stacked chart needs lower and upper Y field values as well as the rest
                String y = vis.fY.get(0).asField();
                needed.add(y + "$lower");
                needed.add(y + "$upper");
            }
            Collections.addAll(needed, vis.usedFields(true));
        }

        // We always want the row field and selection
        needed.add("#row");
        needed.add("#selection");

        // Convert to map for easy lookup
        Map<String, Integer> result = new HashMap<>();
        for (String s : needed) result.put(s, result.size());
        return result;
    }

    private void addElementExports(VisSingle vis, D3DataBuilder dataBuilder, ElementStructure structure) {
        out.add("return {").indentMore();
        out.onNewLine().add("data:").at(24).add("function() { return processed },");
        out.onNewLine().add("original:").at(24).add("function() { return original },");
        out.onNewLine().add("internal:").at(24).add("function() { return data },");
        out.onNewLine().add("selection:").at(24).add("function() { return selection },");
        out.onNewLine().add("makeData:").at(24).add("makeData,");
        out.onNewLine().add("build:").at(24).add("build,");
        out.onNewLine().add("chart:").at(24).add("function() { return charts[" + structure.chart.chartIndex + "] },");
        out.onNewLine().add("group:").at(24).add("function() { return elementGroup },");
        out.onNewLine().add("fields: {").indentMore();
        out.mark();

        writeFieldName("x", vis.fX);
        if (vis.fRange != null)
            writeFieldName("y", Arrays.asList(vis.fRange));
        else
            writeFieldName("y", vis.fY);

        List<String> keys = dataBuilder.makeKeyFields();
        if (!keys.isEmpty()) {
            writeFieldName("key", keys);
        }

        writeFieldName("color", vis.fColor);
        writeFieldName("size", vis.fSize);
        writeFieldName("opacity", vis.fOpacity);
        out.onNewLine().indentLess().add("}");
        out.indentLess().onNewLine().add("}").endStatement();
    }

    private String makeElementTransform(Coordinates coords) {
        if (coords == Coordinates.transposed)
            return "attr('transform','matrix(0,1,1,0,0,0)')";
        else if (coords == Coordinates.polar)
            return makeTranslateTransform("geom.inner_width/2", "geom.inner_height/2");
        else
            return null;
    }

    private void writeFieldName(String name, List fieldNames) {
        if (fieldNames.isEmpty()) return;
        if (out.changedSinceMark()) out.add(",");
        List<String> names = new ArrayList<>();
        for (Object p : fieldNames) {
            if (p instanceof Param) names.add(((Param) p).asField());
            else names.add(p.toString());
        }
        out.onNewLine().add(name, ":").at(24).add("[").addQuotedCollection(names).add("]");
    }

    private void writeMainGroups(ChartStructure structure) {
        SVGGroupUtility groupUtil = new SVGGroupUtility(structure, "chart" + structure.chartID(), out);

        if (structure.nested()) {
            out.onNewLine().comment("Nesting -- create an outer chart and place groups inside for each facet");

            // We only want one outer group, but this function gets called for each facet, so check to see if it is
            // present and only create the chart group if it has not already been created.
            out.add("var outer = vis.select('g." + groupUtil.className + "')").endStatement();
            out.add("if (outer.empty()) outer = ", groupUtil.createChart()).endStatement();

            // Now create the facet group that will contain the chart with data for the indicated facet
            out.add("var chart = outer.append('g').attr('class', 'facet')");
        } else {
            // For non-faceted charts, we only need the simple chart group to hold all the other parts
            out.add("var chart = ", groupUtil.createChart());
        }

        if (hasMultipleCharts) groupUtil.addAccessibleChartInfo();

        out.addChained(makeTranslateTransform("geom.chart_left", "geom.chart_top"))
                .endStatement();

        out.add("chart.append('rect').attr('class', 'background')")
                .add(".attr('width', geom.chart_right-geom.chart_left).attr('height', geom.chart_bottom-geom.chart_top)")
                .endStatement();

        String axesTransform = makeTranslateTransform("geom.inner_left", "geom.inner_top");

        out.add("var interior = chart.append('g').attr('class', 'interior')")
                .addChained(axesTransform);

        // Nested charts do not need additional clipping
        if (!structure.nested()) groupUtil.addClipPathReference(out);

        out.endStatement();
        out.add("interior.append('rect').attr('class', 'inner')")
                .add(".attr('width', geom.inner_width).attr('height', geom.inner_height)")
                .endStatement();
        out.add("var gridGroup = interior.append('g').attr('class', 'grid')")
                .endStatement();

        interaction.addPrerequisites();

        if (scalesBuilder.needsAxes())
            out.add("var axes = chart.append('g').attr('class', 'axis')")
                    .addChained(axesTransform).endStatement();
        if (scalesBuilder.needsLegends()) {
            out.add("var legends = chart.append('g').attr('class', 'legend')")
                    .addChained(makeTranslateTransform("(geom.chart_right-geom.chart_left - 3)", "0"));
            groupUtil.addAccessibleTitle("Legend");
            out.endStatement();
        }

        if (!structure.nested()) groupUtil.defineInnerClipPath();
    }

    private String makeTranslateTransform(String dx, String dy) {
        return "attr('transform','translate(' + " + dx + " + ',' + " + dy + " + ')')";
    }

    public Controls getControls() {
        return controls;
    }

    public String makeStyleSheets() {
        String pattern = "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"%s\" charset=\"utf-8\"/>\n";

        String base;
        if (options.locJavaScript.startsWith("file")) {
            base = String.format(pattern, options.locJavaScript + "/Brunel.css");
        } else {
            base = String.format(pattern, options.locJavaScript + "/brunel." + options.version + ".css");
        }

        if (getControls().isNeeded()) {
            base = base + String.format(pattern, "http://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css")
                    + String.format(pattern, options.locJavaScript + "/sumoselect.css");
        }
        return base;
    }

    public DataTransformParameters modifyParameters(DataTransformParameters params, VisSingle vis) {
        String stackCommand = "";
        String sortRows = params.sortRowsCommand;

        if (vis.stacked) {
            // For stacked data we need to build the stack command
            if (vis.fY.size() > 1) {
                // We have a series
                stackCommand = "#values";
            } else if (vis.fY.size() == 1) {
                // We have a single Y value
                stackCommand = vis.fY.get(0).asField();
            }
            // Apply stacking to the data
            stackCommand += "; " + Data.join(vis.fX) + "; " + Data.join(vis.aestheticFields()) + "; " + vis.tElement.producesSingleShape;
        } else if (vis.tElement == Element.line || vis.tElement == Element.area) {
            // If we have stacked, we do not need to do anything as it sorts the data. Otherwise ...
            // d3 needs the data sorted by 'x' order for lines and paths
            // If we have defined 'x' order, that takes precedence
            String x = vis.fX.get(0).asField() + ":ascending";
            if (sortRows.isEmpty())
                sortRows = x;
            else
                sortRows = sortRows + "; " + x;
        }

        // Replace the stack and sort commands with updated versions
        return new DataTransformParameters(params.constantsCommand, params.filterCommand, params.eachCommand, params.transformCommand, params.summaryCommand,
                stackCommand, params.sortCommand, sortRows, params.seriesCommand, params.usedCommand);
    }

}
