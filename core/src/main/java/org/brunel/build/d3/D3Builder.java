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
import org.brunel.build.chart.ChartStructure;
import org.brunel.build.controls.Controls;
import org.brunel.build.d3.diagrams.GeoMap;
import org.brunel.build.data.DataTransformParameters;
import org.brunel.build.element.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
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
    private int visWidth, visHeight;            // Overall vis size
    private D3ScaleBuilder scalesBuilder;       // The scales for the current chart
    private D3Interaction interaction;          // Builder for interactions
    private D3ElementBuilder[] elementBuilders; // Builder for each element

    private D3Builder(BuilderOptions options) {
        super(options);
    }

    public Object getVisualization() {
        return out.content();
    }

    public String makeImports() {

        String pattern = "\t<script src=\"%s\" charset=\"utf-8\"></script>\n";

        String base = COPYRIGHT_COMMENTS +
                String.format(pattern, "http://cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min.js");

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

        // Create the scales and element builders
        createBuilders(structure, chartMargins);

        // Write the class definition function
        out.titleComment("Define chart #" + structure.chartID(), "in the visualization");
        out.add("charts[" + structure.chartIndex + "] = function(parentNode, filterRows) {").ln();
        out.indentMore();

        double[] margins = scalesBuilder.marginsTLBR();

        out.add("var geom = BrunelD3.geometry(parentNode || vis.node(),", chartMargins, ",", margins, "),")
                .indentMore()
                .onNewLine().add("elements = [];").at(50).comment("Array of elements in this chart")
                .indentLess();

        // Transpose if needed
        if (scalesBuilder.coords == VisTypes.Coordinates.transposed) out.add("geom.transpose()").endStatement();

        for (D3ElementBuilder builder : elementBuilders) builder.writePerChartDefinitions();

        // Now build the main groups
        out.titleComment("Define groups for the chart parts");
        interaction = new D3Interaction(structure, scalesBuilder, out);
        writeMainGroups(structure);

        // Define geo projection when needed
        if (structure.geo != null) {
            // Write the projection
            out.titleComment("Projection");
            GeoMap.writeProjection(out, structure.geo);
        }

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

        ElementStructure[] structures = structure.elementStructure;
        elementBuilders = new D3ElementBuilder[structures.length];
        for (int i = 0; i < structures.length; i++)
            elementBuilders[i] = new D3ElementBuilder(structures[i], out, scalesBuilder);
    }

    protected void defineElement(ElementStructure structure) {
        D3ElementBuilder elementBuilder = elementBuilders[structure.index];

        out.titleComment("Define element #" + structure.elementID());
        out.add("elements[" + structure.index + "] = function() {").indentMore();
        out.onNewLine().add("var original, processed,").at(40).comment("data sets passed in and then transformed")
                .indentMore()
                .onNewLine().add("element, data,").at(40).comment("Brunel element information and brunel data")
                .onNewLine().add("d3Data, selection;").at(40).comment("D3 version of data and D3 selection")
                .indentLess();

        // Add data variables used throughout
        addElementGroups(elementBuilder, "element" + structure.elementID());

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
        interaction.addElementHandlers(structure.vis);
        out.indentLess().onNewLine().add("}").ln().ln();

        // Expose the methods and variables we want the user to have access to
        addElementExports(vis);

        out.indentLess().onNewLine().add("}()").endStatement().ln();
    }

    protected void defineVisSystem(VisItem main, int width, int height) {
        this.visWidth = width;
        this.visHeight = height;
        this.out = new ScriptWriter(options);

        // Write the class definition function (and flag to use strict mode)
        out.add("function ", options.className, "(visId) {").ln().indentMore();
        out.add("\"use strict\";").comment("Strict Mode");

        // Add commonly used definitions
        out.add("var datasets = [],").at(50).comment("Array of datasets for the original data");
        out.add("    pre = function(d, i) { return d },").at(50).comment("Default pre-process does nothing");
        out.add("    post = function(d, i) { return d },").at(50).comment("Default post-process does nothing");
        out.add("    transitionTime = 200,").at(50).comment("Transition time for animations");
        out.add("    charts = [],").at(50).comment("The charts in the system");
        out.add("    vis = d3.select('#' + visId).attr('class', 'brunel');").at(60).comment("the SVG container");
    }

    protected void endChart(ChartStructure structure) {
        out.onNewLine().add("function build(time) {").indentMore();
        out.onNewLine().add("var first = elements[0].data() == null").endStatement();
        out.add("if (first) time = 0;").comment("No transition for first call");

        if (scalesBuilder.needsAxes()) out.onNewLine().add("buildAxes(); ");

        Integer[] order = structure.elementBuildOrder();

        out.onNewLine().add("if (first || time>0) ");
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
        out.add("return { build : build, elements : elements }").endStatement();

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
            out.add("function updateAll(time) { charts.forEach(function(x) {x.build(time || 20)}) }").ln();
        } else {
            // For nesting, need a custom update method
            // TODO make work for more than two charts1
            out.add("function updateAll(time) {").indentMore().ln()
                    .add("var t = time || 20").endStatement()
                    .add("charts[0].build(0)").endStatement()
                    .add("BrunelD3.facet(charts[1], charts[0].elements[0], t)").endStatement()
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

    private void addElementGroups(D3ElementBuilder builder, String elementID) {
        String elementTransform = makeElementTransform(scalesBuilder.coords);
        out.add("var elementGroup = interior.append('g').attr('class', '" + elementID + "')");
        if (elementTransform != null) out.addChained(elementTransform);
        if (builder.needsDiagramExtras())
            out.continueOnNextLine(",").add("diagramExtras = elementGroup.append('g').attr('class', 'extras')");
        out.continueOnNextLine(",").add("main = elementGroup.append('g').attr('class', 'main')");
        if (builder.needsDiagramLabels())
            out.continueOnNextLine(",")
                    .add("diagramLabels = BrunelD3.undoTransform(elementGroup.append('g').attr('class', 'diagram labels'), elementGroup)");

        out.continueOnNextLine(",")
                .add("labels = BrunelD3.undoTransform(elementGroup.append('g').attr('class', 'labels'), elementGroup)").endStatement();
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

    private void addElementExports(VisSingle vis) {
        out.add("return {").indentMore();
        out.onNewLine().add("data:").at(24).add("function() { return processed },");
        out.onNewLine().add("internal:").at(24).add("function() { return data },");
        out.onNewLine().add("selection:").at(24).add("function() { return selection },");
        out.onNewLine().add("makeData:").at(24).add("makeData,");
        out.onNewLine().add("build:").at(24).add("build,");
        out.onNewLine().add("fields: {").indentMore();
        out.mark();
        writeFieldName("x", vis.fX);
        writeFieldName("y", vis.fY);
        writeFieldName("color", vis.fColor);
        writeFieldName("size", vis.fSize);
        writeFieldName("opacity", vis.fOpacity);
        out.onNewLine().indentLess().add("}");
        out.indentLess().onNewLine().add("}").endStatement();
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
        if (fieldNames.isEmpty()) return;
        if (out.changedSinceMark()) out.add(",");
        List<String> names = new ArrayList<String>();
        for (Param p : fieldNames) names.add(p.asField());
        out.onNewLine().add(name, ":").at(24).add("[").addQuotedCollection(names).add("]");
    }

    private void writeMainGroups(ChartStructure structure) {
        if (structure.nested()) {
            out.add("vis = d3.select(parentNode.parentNode);").comment("nested charts top is not the real top");
        }
        out.add("var chart = vis.append('g').attr('class', '" + "chart" + structure.chartID() + "')")
                .addChained(makeTranslateTransform("geom.chart_left", "geom.chart_top"))
                .endStatement();
        out.add("chart.append('rect').attr('class', 'background')")
                .add(".attr('width', geom.chart_right-geom.chart_left).attr('height', geom.chart_bottom-geom.chart_top)")
                .endStatement();


        String axesTransform = makeTranslateTransform("geom.inner_left", "geom.inner_top");

        out.add("var interior = chart.append('g').attr('class', 'interior')")
                .addChained(axesTransform);

        // Nested charts do not need additional clipping
        if (!structure.nested()) out.addChained("attr('clip-path', 'url(#" + clipID(structure) + ")')");

        out.endStatement();
        out.add("interior.append('rect').attr('class', 'inner')")
                .add(".attr('width', geom.inner_width).attr('height', geom.inner_height)")
                .endStatement();

        interaction.addPrerequisites();

        if (scalesBuilder.needsAxes())
            out.add("var axes = chart.append('g').attr('class', 'axis')")
                    .addChained(axesTransform).endStatement();
        if (scalesBuilder.needsLegends())
            out.add("var legends = chart.append('g').attr('class', 'legend')")
                    .addChained(makeTranslateTransform("(geom.outer_width - geom.chart_right - 3)", "0")).endStatement();

        if (!structure.nested()) {
            // Make the clip path for this: we expand by a pixel to avoid ugly cut-offs right at the edge
            out.add("vis.append('clipPath').attr('id', '" + clipID(structure) + "').append('rect')");
            out.addChained("attr('x', -1).attr('y', -1)");
            if (scalesBuilder.coords == VisTypes.Coordinates.transposed)
                out.addChained("attr('width', geom.inner_height+2).attr('height', geom.inner_width+2)").endStatement();
            else
                out.addChained("attr('width', geom.inner_width+2).attr('height', geom.inner_height+2)").endStatement();
        }
    }

    private String makeTranslateTransform(String dx, String dy) {
        return "attr('transform','translate(' + " + dx + " + ',' + " + dy + " + ')')";
    }

    // returns an id that is unique to the chart and the visualization
    private String clipID(ChartStructure structure) {
        return "clip_" + options.visIdentifier + "_" + structure.chartID();
    }

    public Controls getControls() {
        return controls;
    }

    public String makeStyleSheets() {
        String pattern = "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"%s\" charset=\"utf-8\"/>\n";

        String base;
        if (options.locJavaScript.startsWith("file")) {
            base = String.format(pattern, options.locJavaScript + "/BrunelBaseStyles.css");
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
        } else if (vis.tElement == VisTypes.Element.line || vis.tElement == VisTypes.Element.area) {
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
