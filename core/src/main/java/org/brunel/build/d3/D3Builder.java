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
import org.brunel.build.DataTransformParameters;
import org.brunel.build.ElementDependency;
import org.brunel.build.controls.Controls;
import org.brunel.build.d3.diagrams.GeoMap;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/*
        The methods of this class are called as an Abstract Builder to build the chart
 */
public class D3Builder extends AbstractBuilder {

    private static final String COPYRIGHT_COMMENTS = "<!--\n" +
            "\tD3 Copyright © 2012, Michael Bostock\n" +
            "\tjQuery Copyright © 2010 by The jQuery Project\n" +
            "\tsumoselect Copyright © 2014 Hemant Negi\n " +
            "-->\n";

    private D3Builder(BuilderOptions options) {
        super(options);
    }

    public String makeImports() {

        String pattern = "<script src=\"%s\" charset=\"utf-8\"></script>\n";

        String base = COPYRIGHT_COMMENTS +
                String.format(pattern, "http://cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min.js");

        if (getControls().isNeeded()) {
            base = base + String.format(pattern, "http://code.jquery.com/jquery-1.10.2.js")
                    + String.format(pattern, "http://code.jquery.com/ui/1.11.4/jquery-ui.js");
        }

        if (options.localResources == null) {
            base = base + String.format(pattern, "http://brunelvis.org/js/brunel." + options.version + ".min.js");
            if (getControls().isNeeded())
                base = base + String.format(pattern, "http://brunelvis.org/js/brunel.controls." + options.version + ".min.js");
        } else {
            base = base
                    + String.format(pattern, options.localResources + "/BrunelData.js")
                    + String.format(pattern, options.localResources + "/BrunelD3.js");
            if (getControls().isNeeded()) base = base
                    + String.format(pattern, options.localResources + "/BrunelEventHandlers.js")
                    + String.format(pattern, options.localResources + "/BrunelJQueryControlFactory.js")
                    + String.format(pattern, options.localResources + "/sumoselect/jquery.sumoselect.min.js");
        }
        return base;
    }

    public String makeStyleSheets() {
        String pattern = "<link rel=\"stylesheet\" type=\"text/css\" href=\"%s\" charset=\"utf-8\"></script>\n";

        String base;
        if (options.localResources == null) {
            base = String.format(pattern, "http://brunelvis.org/js/brunel." + options.version + ".css");
        } else {
            base = String.format(pattern, options.localResources + "/BrunelBaseStyles.css");
        }

        if (getControls().isNeeded()) {
            base = base + String.format(pattern, "http://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css")
                    + String.format(pattern, "http://brunelvis.org/js/sumoselect.css");
        }
        return base;
    }

    /**
     * Return the required builder
     */
    public static D3Builder make(BuilderOptions options) {
        return new D3Builder(options);
    }

    /**
     * Return the required builder with default options
     */
    public static D3Builder make() {
        return make(new BuilderOptions());
    }

    private ScriptWriter out;                   // Where to write code
    private int chartIndex;                     // Current chart index
    private int elementIndex;                   // Current element index
    private int visWidth, visHeight;            // Overall vis size
    private String chartClass;                  // Current chart parent class
    private D3ScaleBuilder scalesBuilder;       // The scales for the current chart
    private D3Interaction interaction;          // Builder for interactions
    private PositionFields positionFields;      // Information on fields used for position
    private Integer[] elementBuildOrder;        // Order to build the elements

    public Object getVisualization() {
        return out.content();
    }

    protected String defineVisSystem(VisItem main, int width, int height) {
        this.visWidth = width;
        this.visHeight = height;
        this.out = new ScriptWriter(options.readableJavascript);
        this.chartIndex = 0;

        // Write the class definition function (and flag to use strict mode)
        out.add("function ", options.className, "(visId) {").ln().indentMore();
        out.add("\"use strict\";").comment("Strict Mode");

        // Add commonly used definitions
        out.onNewLine().ln();
        out.add("var datasets = [],").at(40).comment("Array of datasets for the original data");
        out.add("    pre = function(d, i) { return d },").at(40).comment("Default pre-process does nothing");
        out.add("    post = function(d, i) { return d },").at(40).comment("Default post-process does nothing");
        out.add("    transitionTime = 200,").at(40).comment("Transition time for animations");
        out.add("    charts = [],").at(40).comment("The charts in the system");
        out.add("    vis = d3.select('#' + visId).attr('class', 'brunel')").comment("the SVG container");

        return options.visIdentifier;
    }

    protected String defineChart(double[] location, VisSingle[] elements, Dataset[] elementData) {

        this.positionFields = new PositionFields(elements, elementData);
        this.elementBuildOrder = makeBuildOrder(elements);

        elementIndex = 0;

        chartClass = "chart" + (chartIndex + 1);
        double[] chartMargins = new double[]{
                (visHeight * location[0] / 100), (visWidth * location[1] / 100),
                (visHeight * (1 - location[2] / 100)), (visWidth * (1 - location[3] / 100))
        };

        // Write the class definition function
        out.titleComment("Define chart #" + chartIndex, "in the visualization");
        out.add("charts[" + chartIndex, "] = function() {").ln();
        out.indentMore();

        // Define scales and geom (we need the scales to get axes sizes, which determines geom)
        double chartWidth = visWidth - chartMargins[1] - chartMargins[3];
        double chartHeight = visHeight - chartMargins[0] - chartMargins[2];
        this.scalesBuilder = new D3ScaleBuilder(elements, elementData, positionFields, chartWidth, chartHeight, out);
        double[] margins = scalesBuilder.marginsTLBR();
        out.add("var geom = BrunelD3.geometry(vis.node(),", chartMargins, ",", margins, ")").endStatement();

        // Transpose if needed
        if (scalesBuilder.coords == VisTypes.Coordinates.transposed) out.add("geom.transpose()").endStatement();

        // Define the list of elements
        out.add("var elements = [];").at(40).comment("Array of elements in this chart");

        // Now build the main groups
        out.titleComment("Define groups for the chart parts");
        interaction = new D3Interaction(elements, positionFields, scalesBuilder, out);
        writeMainGroups();

        // Define scales and access functions
        if (scalesBuilder.isGeo()) {
            out.titleComment("Projection");

            // Write the projection for that
            GeoMap.writeProjection(out, scalesBuilder.geo.bounds());

        } else {

            out.titleComment("Scales");
            scalesBuilder.writeCoordinateScales(interaction);

            // Define the Axes
            if (scalesBuilder.needsAxes()) {
                out.titleComment("Axes");
                scalesBuilder.writeAxes();
            }
        }

        chartIndex++;
        return chartClass;
    }

    /*
            If we have a dependency between elements, for example, between a node and edge chart,
            we will need to build taking that into account -- the nodes first, then the edges
     */
    private Integer[] makeBuildOrder(final VisSingle[] elements) {
        // Start with the default order
        Integer[] order = new Integer[elements.length];
        for (int i = 0; i < order.length; i++) order[i] = i;

        // Sort so elements with most keys go last
        // This works at least for nodes and links at least; when we add new cases, we'll need a more complex function
        Arrays.sort(order, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return elements[a].fKeys.size() - elements[b].fKeys.size();
            }
        });

        return order;
    }

    protected String defineElement(VisSingle vis, Dataset data, int datasetIndex, ElementDependency dependency) {
        out.titleComment("Define element #" + (elementIndex + 1));
        out.add("elements[" + elementIndex + "] = function() {").indentMore();
        out.onNewLine().add("var original, processed,").at(40).comment("data sets passed in and then transformed")
                .indentMore()
                .onNewLine().add("element,").at(40).comment("Brunel element information")
                .onNewLine().add("data, layout,").at(40).comment("Brunel data and layout method")
                .onNewLine().add("d3Data, d3Layout,").at(40).comment("D3 versions")
                .onNewLine().add("selection;").at(40).comment("D3 selection")
                .indentLess();

        // Add data variables used throughout
        addElementGroups();

        // Data transforms
        D3DataBuilder dataBuilder = new D3DataBuilder(vis, out, data, datasetIndex);
        dataBuilder.writeDataManipulation(createResultFields(vis));

        scalesBuilder.writeAestheticScales(vis);
        scalesBuilder.writeLegends(vis);

        defineElementBuildFunction(vis, data, dependency);

        // Expose the methods and variables we want the user to have access to
        addElementExports(vis);

        out.indentLess().onNewLine().add("}()").endStatement().ln();
        return "element" + (++elementIndex);
    }

    /*
        Builds a mapping from the fields we will use in the built data object to an indexing 0,1,2,3, ...
     */
    private Map<String, Integer> createResultFields(VisSingle vis) {
        LinkedHashSet<String> needed = new LinkedHashSet<String>();
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

        // We always want the row field
        needed.add("#row");

        // Convert to map for easy lookup
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (String s : needed) result.put(s, result.size());
        return result;
    }

    private void defineElementBuildFunction(VisSingle vis, Dataset data, ElementDependency dependency) {

        D3ElementBuilder elementBuilder = new D3ElementBuilder(vis, out, scalesBuilder, positionFields, data, dependency);
        elementBuilder.preBuildDefinitions();

        // Main method to make a vis
        out.titleComment("Build element from data");

        out.add("function build(transitionMillis) {").ln().indentMore();
        elementBuilder.generate(elementIndex);
        interaction.addElementHandlers(vis);
        out.indentLess().onNewLine().add("}").endStatement().ln();
    }

    protected void endVisSystem(VisItem main, String currentVisualizationID) {

        // Define visualization functions
        out.titleComment("Expose the needed Visualization functions and fields");
        out.add("function setData(rowData, i) { datasets[i||0] = BrunelData.Dataset.makeFromRows(rowData) }").endStatement();
        out.add("function getData(i) { return datasets[i||0] }").endStatement();

        out.add("function buildSystem() {").ln().indentMore()
                .add("for (var i=0;i<arguments.length;i++) setData(arguments[i], i)").endStatement()
                .add("charts.forEach(function(x) {x.build(transitionTime)})").endStatement();
        out.ln().indentLess().add("}").endStatement().ln();

        out.add("function rebuildSystem(time) {").ln().indentMore()
                .add("time = (time == null) ? 20 : time").endStatement()
                .add("charts.forEach(function(x) {x.build(time)})").endStatement();
        out.ln().indentLess().add("}").endStatement().ln();

        // Return the important items
        out.add("return {").indentMore().ln()
                .add("dataPreProcess:").at(24).add("function(f) { if (f) pre = f; return pre },").ln()
                .add("dataPostProcess:").at(24).add("function(f) { if (f) post = f; return post },").ln()
                .add("data:").at(24).add("function(d,i) { if (d) setData(d,i); return datasets[i||0] },").ln()
                .add("visId:").at(24).add("visId,").ln()
                .add("build:").at(24).add("buildSystem,").ln()
                .add("rebuild:").at(24).add("rebuildSystem,").ln()
                .add("charts:").at(24).add("charts").ln()
                .indentLess().add("}").endStatement();

        // Finish the outer class definition
        out.indentLess().onNewLine().add("}").endStatement();

        // Create the initial raw data table
        D3DataBuilder.writeTables(main, out, options);

        // Call the function on the data
        if (options.generateBuildCode) {
            out.titleComment("Call Code to Build the system");
            out.add("var v = new", options.className, "(" + out.quote(options.visIdentifier) + ")").endStatement();
            int length = main.getDataSets().length;
            out.add("v.build(");
            for (int i = 0; i < length; i++) {
                if (i > 0) out.add(", ");
                out.add(String.format(options.dataName, i + 1));
            }
            out.add(")").endStatement();
        }

        // Add controls code
        controls.write(out);

    }

    public Controls getControls() {
        return controls;
    }

    protected void endChart(String currentChartID) {
        out.onNewLine().add("function build(time) {").indentMore();
        out.onNewLine().add("var first = elements[0].data() == null").endStatement();
        out.add("if (first) time = 0;").comment("No transition for first call");

        if (scalesBuilder.needsAxes()) out.onNewLine().add("buildAxes(); ");

        out.onNewLine().add("if (first || time>0) ");
        if (elementBuildOrder.length > 1) {
            out.add("{").indentMore();
            for (int i : elementBuildOrder)
                out.onNewLine().add("elements[" + i + "].makeData();");
            out.indentLess().onNewLine().add("}").endStatement();
        } else {
            out.add("elements[0].makeData()").endStatement();
        }
        for (int i : elementBuildOrder)
            out.onNewLine().add("elements[" + i + "].build(time);");
        out.indentLess().onNewLine().add("}").endStatement().ln();

        out.comment("Expose the following components of the chart");
        out.add("return {").indentMore()
                .onNewLine().add("build : build,")
                .onNewLine().add("elements : elements")
                .onNewLine().indentLess().add("}").endStatement();
        // Finish the chart method
        out.indentLess().add("}()").endStatement().ln();
    }

    protected DataTransformParameters modifyParameters(DataTransformParameters params, VisSingle vis) {
        String stackCommand = "";
        String sortCommand = params.sortCommand;

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
        } else if (vis.tElement == VisTypes.Element.line || vis.tElement == VisTypes.Element.area) {
            // If we have stacked, we do not need to do anything as it sorts the data. Otherwise ...
            // d3 needs the data sorted by 'x' order for lines and paths
            // If we have defined 'x' order, that takes precedence
            String x = vis.fX.get(0).asField() + ":ascending";
            if (sortCommand.isEmpty())
                sortCommand = x;
            else
                sortCommand = sortCommand + "; " + x;
        }

        // Replace the stack and sort commands with updated versions
        return new DataTransformParameters(params.constantsCommand, params.filterCommand, params.transformCommand, params.summaryCommand,
                stackCommand, sortCommand, params.seriesCommand, params.usedCommand);
    }

    private void writeMainGroups() {
        String axesTransform = makeTranslateTransform("geom.inner_left", "geom.inner_top");
        out.add("var chart = vis.append('g').attr('class', '" + chartClass + "')")
                .addChained(makeTranslateTransform("geom.chart_left", "geom.chart_top"))
                .endStatement();
        out.add("var interior = chart.append('g').attr('class', 'interior')")
                .addChained(axesTransform)
                .addChained("attr('clip-path', 'url(#" + clipID() + ")')")
                .endStatement();

        interaction.addPrerequisites();

        out.add("var axes = chart.append('g').attr('class', 'axis')")
                .addChained(axesTransform).endStatement();
        out.add("var legends = chart.append('g').attr('class', 'legend')")
                .addChained(makeTranslateTransform("geom.outer_width", "0")).endStatement();

        // Make the clip path for this: we expand by a pixel to avoid ugly cut-offs right at the edge
        out.add("vis.append('clipPath').attr('id', '" + clipID() + "').append('rect')");
        out.addChained("attr('x', -1).attr('y', -1)");
        if (scalesBuilder.coords == VisTypes.Coordinates.transposed)
            out.addChained("attr('width', geom.inner_height+2).attr('height', geom.inner_width+2)").endStatement();
        else
            out.addChained("attr('width', geom.inner_width+2).attr('height', geom.inner_height+2)").endStatement();

    }

    private void addElementGroups() {
        String elementTransform = makeElementTransform(scalesBuilder.coords);
        out.add("var elementGroup = interior.append('g').attr('class', 'element" + (elementIndex + 1) + "')");
        if (elementTransform != null) out.addChained(elementTransform);
        if (scalesBuilder.isDiagram)
            out.continueOnNextLine(",").add("diagramExtras = elementGroup.append('g').attr('class', 'extras')");
        out.continueOnNextLine(",").add("main = elementGroup.append('g').attr('class', 'main')");
        if (scalesBuilder.isDiagram)
            out.continueOnNextLine(",").add("diagramLabels = elementGroup.append('g').attr('class', 'diagram labels')");
        out.continueOnNextLine(",").add("labels = elementGroup.append('g').attr('class', 'labels')").endStatement();
    }

    // returns an id that is unique to the chart and the visualization
    private String clipID() {
        return "clip_" + options.visIdentifier + "_" + chartIndex;
    }

    private void addElementExports(VisSingle vis) {
        out.add("return {").indentMore();
        out.onNewLine().add("data:").at(24).add("function() { return processed },");
        out.onNewLine().add("internal:").at(24).add("function() { return data },");
        out.onNewLine().add("makeData:").at(24).add("makeData,");
        out.onNewLine().add("build:").at(24).add("build,");
        out.onNewLine().add("fields: {").indentMore();
        writeFieldName("x", vis.fX);
        writeFieldName("y", vis.fY);
        writeFieldName("color", vis.fColor);
        writeFieldName("size", vis.fSize);
        writeFieldName("opacity", vis.fOpacity);
        out.onNewLine().indentLess().add("}");
        out.indentLess().onNewLine().add("}").endStatement();
    }

    private String makeTranslateTransform(String dx, String dy) {
        return "attr('transform','translate(' + " + dx + " + ',' + " + dy + " + ')')";
    }

    private String makeElementTransform(VisTypes.Coordinates coords) {
        if (coords == VisTypes.Coordinates.transposed)
            return "attr('transform','matrix(0,1,1,0,0,0)')";
        else if (coords == VisTypes.Coordinates.polar)
            return makeTranslateTransform("geom.inner_width/2", "geom.inner_height/2");
        else
            return null;
    }

    private void writeFieldName(String name, List<Param> fieldNames) {
        List<String> names = new ArrayList<String>();
        for (Param p : fieldNames) names.add(p.asField());
        if (!fieldNames.isEmpty())
            out.onNewLine().add(name, ":").at(24).add("[").addQuotedCollection(names).add("],");
    }

}
