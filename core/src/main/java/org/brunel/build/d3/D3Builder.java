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
import org.brunel.build.AbstractBuilder;
import org.brunel.build.DataTransformParameters;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/*
        The methods of this class are called as an Abstract Builder to build the chart
 */
public class D3Builder extends AbstractBuilder {

    public static String imports(String... jsScriptLocations) {
        String result = "";
        for (String s : jsScriptLocations) result += "<script src=\"" + s + "\" charset=\"utf-8\"></script>\n";
        return result;
    }

    private final String visIdentifier;         // Identifier for the HTML element
    private final String dataName;              // Name of the row data to use
    private final String className;             // Name of the brunel function to create
    private final boolean addGenerationCode;    // If true, we add a call to the build at the end of the definitions
    private ScriptWriter out;                   // Where to write code
    private int chartIndex;                     // Current chart index
    private int elementIndex;                   // Current element index
    private int visWidth, visHeight;            // Overall vis size
    private String chartClass;                  // Current chart parent class
    private D3ScaleBuilder scalesBuilder;       // The scales for the current chart
    private D3Interaction interaction;          // Builder for interactions
    private PositionFields positionFields;      // Information on fields used for position

    /**
     * Create a new d3 builder that will assign the "visId" JS variable to the supplied
     * value.  This should be the value of the HTML element (likely SVG element) that holds the
     * d3 content.
     *
     * @param visIdentifier the id to use.
     */

    public D3Builder(String visIdentifier) {
        this(visIdentifier, "table", "BrunelVis", true);
    }

    /**
     * Create a new d3 builder that will assign the "visId" JS variable to the supplied
     * value.  This should be the value of the HTML element (likely SVG element) that holds the
     * d3 content.
     *
     * @param visIdentifier     the id to use.
     * @param dataName          Name of the row data to use
     * @param className         Name of the brunel class to create
     * @param addGenerationCode if true, code will be added to generate the chart
     */

    public D3Builder(String visIdentifier, String dataName, String className, boolean addGenerationCode) {
        this.visIdentifier = visIdentifier;
        this.dataName = dataName;
        this.className = className;
        this.addGenerationCode = addGenerationCode;
    }

    public Object getVisualization() {
        return out.content();
    }

    protected String defineVisSystem(VisItem main, int width, int height) {
        this.visWidth = width;
        this.visHeight = height;
        this.out = new ScriptWriter();
        this.chartIndex = 0;

        // Write the class definition function (and flag to use strict mode)
        out.add("function ", className, "(visId) {").ln().indentMore();
        out.add("\"use strict\";").comment("Strict Mode");

        // Add commonly used definitions
        out.onNewLine().ln();
        out.add("var datasets = [],").at(39).comment("Array of datasets for the original data");
        out.add("    pre = function(d, i) { return d },").at(39).comment("Default pre-process does nothing");
        out.add("    post = function(d, i) { return d },").at(39).comment("Default post-process does nothing");
        out.add("    transitionTime = 200,").at(39).comment("Transition time for animations");
        out.add("    vis = d3.select('#' + visId).attr('class', 'brunel')").comment("the SVG container");

        return visIdentifier;
    }

    protected String defineChart(double[] location, VisSingle[] elements, Dataset[] elementData) {

        this.positionFields = new PositionFields(elements, elementData);

        chartIndex++;
        elementIndex = 0;

        chartClass = "chart" + chartIndex;
        double[] chartMargins = new double[]{
                (visHeight * location[0] / 100), (visWidth * location[1] / 100),
                (visHeight * (1 - location[2] / 100)), (visWidth * (1 - location[3] / 100))
        };

        // Write the class definition function
        out.titleComment("Define chart #" + chartIndex, "in the visualization");
        out.add("function chart" + chartIndex, "() {").ln();
        out.indentMore();

        // Define scales and geometry (we need the scales to get axes sizes, which determines geometry)
        double chartWidth = visWidth - chartMargins[1] - chartMargins[3];
        double chartHeight = visHeight - chartMargins[0] - chartMargins[2];
        this.scalesBuilder = new D3ScaleBuilder(elements, elementData, positionFields, chartWidth, chartHeight, out);
        double[] margins = scalesBuilder.marginsTLBR();
        out.add("var geom = BrunelD3.geometry(vis.node(),", chartMargins, ",", margins, ")").endStatement();

        // Transpose if needed
        if (scalesBuilder.coords == VisTypes.Coordinates.transposed) out.add("geom.transpose()").endStatement();

        // Now build the main groups
        out.titleComment("Define groups for the chart parts");
        interaction = new D3Interaction(elements, positionFields, scalesBuilder, out);
        writeMainGroups();

        // Define scales and access functions
        out.titleComment("Scales");
        scalesBuilder.writeCoordinateScales(interaction);

        // Define the Axes
        out.titleComment("Axes");
        scalesBuilder.writeAxes();

        return chartClass;
    }

    protected String defineElement(VisSingle vis, Dataset data, int datasetIndex) {
        out.titleComment("Closure defining " + (elementIndex + 1));
        String name = "element" + (elementIndex + 1);
        out.add("var", name, "= function() {").indentMore();

        // Add data variables used throughout
        out.onNewLine().add("var raw, base, data;").at(40).comment("The processed data and the data object");
        addElementGroups(elementIndex);

        // Data transforms
        D3DataBuilder dataBuilder = new D3DataBuilder(vis, out, data, datasetIndex);
        dataBuilder.writeDataManipulation(createResultFields(vis));

        scalesBuilder.writeAestheticScales(vis);
        scalesBuilder.writeLegends(vis);

        defineElementBuildFunction(vis, data);

        // Expose the methods and variables we want the user to have access to
        addElementExports(vis);

        out.indentLess().onNewLine().add("}()").endStatement().ln();
        elementIndex++;
        return name;
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

    private void defineElementBuildFunction(VisSingle vis, Dataset data) {

        D3ElementBuilder elementBuilder = new D3ElementBuilder(vis, out, scalesBuilder, positionFields, data);

      // Main method to make a vis
        out.titleComment("Main element build routine");
        out.add("function build(transitionMillis) {").ln().indentMore();
        out.add("transitionMillis = transitionMillis || (data ? transitionTime : 0)")
                .comment("// No transition for first call");
        out.add("if (!data || transitionMillis>0) data = buildData()").endStatement();

        elementBuilder.generate();

        interaction.addElementHandlers(vis);
        out.indentLess().onNewLine().add("}").endStatement().ln();
    }

    protected void endVisSystem(VisItem main, String currentVisualizationID) {

        // Define visualization functions
        out.titleComment("Expose the needed Visualization functions and fields");
        out.add("function setData(rowData, i) { datasets[i ||0] = BrunelData.Dataset.makeFromRows(rowData) }").endStatement();
        out.add("function getData(i) { return datasets[i ||0] }").endStatement();

        out.add("var parts = [ ");
        for (int i = 1; i <= chartIndex; i++) {
            out.add("chart" + i + "()");
            if (i < chartIndex) out.add(", ");
        }
        out.add("]").endStatement();

        out.add("function buildAll() {").ln().indentMore()
                .add("for (var i=0;i<arguments.length;i++) setData(arguments[i], i)").endStatement();
        for (int i = 0; i < chartIndex; i++) out.add("parts[" + i + "].build(); ");
        out.ln().indentLess().add("}").endStatement().ln();

        out.add("function rebuildAll() {").ln().indentMore();
        for (int i = 0; i < chartIndex; i++) out.add("parts[" + i + "].build(20); ");
        out.ln().indentLess().add("}").endStatement().ln();


        // Return the important items
        out.add("return {").indentMore().ln()
                .add("dataPreProcess:").at(24).add("function(f) { if (f) pre = f; return pre },").ln()
                .add("dataPostProcess:").at(24).add("function(f) { if (f) post = f; return post },").ln()
                .add("data:").at(24).add("function(d,i) { if (d) setData(d,i); return datasets[i||0] },").ln()
                .add("visId:").at(24).add("visId,").ln()
                .add("build:").at(24).add("buildAll,").ln()
                .add("rebuild:").at(24).add("rebuildAll,").ln()
                .add("charts:").at(24).add("parts").ln()
                .indentLess().add("}").endStatement();

        // Finish the outer class definition
        out.indentLess().onNewLine().add("}").endStatement();

        // Create the initial raw data table
        D3DataBuilder.writeRawData(main, out);

        // Call the function on the data
        if (addGenerationCode) {
            out.titleComment("Call Code to Build the system");
            out.add("var v = new", className, "(" + out.quote(visIdentifier) + ")").endStatement();
            int length = main.getDataSets().length;
            out.add("v.build(");
            for (int i=0; i<length; i++) {
                if (i>0) out.add(", ");
                out.add(dataName + (i+1));
            }
            out.add(")").endStatement();
        }
    }

    protected void endChart(String currentChartID) {
        out.onNewLine().add("function build(transitionMillis) {").indentMore();
        if (!scalesBuilder.isDiagram) out.onNewLine().add("buildAxes(); ");
        for (int i = 0; i < elementIndex; i++)
            out.onNewLine().add("element" + (i + 1) + ".build(transitionMillis);");
        out.indentLess().onNewLine().add("}").endStatement().ln();

        out.comment("Expose the following components of the chart");
        out.add("return {").indentMore()
                .onNewLine().add("build : build,")
                .onNewLine().add("elements : [");
        for (int i = 0; i < elementIndex; i++)
            out.add(i == 0 ? "" : ", ").add(" element" + (i + 1));
        out.add("]");
        out.onNewLine().indentLess().add("}").endStatement();
        // Finish the chart method
        out.indentLess().add("}").endStatement().ln();
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

    private void addElementGroups(int elementIndex) {
        String elementTransform = makeElementTransform(scalesBuilder.coords);
        out.add("var elementGroup = interior.append('g').attr('class', 'element" + (elementIndex + 1) + "')");
        if (elementTransform != null) out.addChained(elementTransform);
        if (scalesBuilder.isDiagram)
            out.continueOnNextLine(",").add("diagramExtras = elementGroup.append('g').attr('class', 'extras')");
        out.continueOnNextLine(",").add("main = elementGroup.append('g').attr('class', 'main')");
        if (scalesBuilder.isDiagram)
            out.continueOnNextLine(",").add("diagramLabels = elementGroup.append('g').attr('class', 'extras')");
        out.continueOnNextLine(",").add("labels = elementGroup.append('g').attr('class', 'labels')").endStatement();
    }

    // returns an id that is unique to the chart and the visualization
    private String clipID() {
        return "clip_" + visIdentifier + "_" + chartIndex;
    }

    private void addElementExports(VisSingle vis) {
        out.add("return {").indentMore();
        out.onNewLine().add("builtData:").at(24).add("function() { return base },");
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
        if (!fieldNames.isEmpty())
            out.onNewLine().add(name, ":").at(24).add("[").addQuotedCollection(fieldNames).add("],");
    }

    protected String getVisId() {
        return this.visIdentifier;
    }

}
