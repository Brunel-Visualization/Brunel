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

package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.controls.Controls;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisComposition;
import org.brunel.model.VisException;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;
import org.brunel.model.style.StyleSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The abstract builder does as much work as possible in building the visualizations. A descendant of this class
 * must define the specific methods that will define the main artifacts.
 *
 * A rough flow of the build process is as follows:
 *
 * <ul>
 * <li> A 'visualization' is created; this may consist of multiple charts each with different parts in it,
 * but the entire system is defined within one area. This is equivalent to being within a single 'div'
 * in a browser, or a single AWT/SWT control in a java application.
 * <li> Individual charts are defined within the visualization; for each of them the following steps are performed:
 * * 'createVis' is called to create a chart within a defined sub-area
 * * Within each chart may be multiple <code>VisSingle</code> items, for each of them we:
 * <li> collect controls and style overrides into the relevant classes
 * <li> build the data for that element
 * <li> call 'createSingle' to build the single visualization (e.g. a bar in combination bar/line chart).
 * </ul>
 *
 * A builder may be called multiple times; every call to 'build' will reset the state and start from new
 */
public abstract class AbstractBuilder implements Builder {

    /**
     * Default layouts, in percent coordinates. The outer array indexes the total number of charts, and
     * the next one indexes the chart within the order. The charts are assumed to be in importance order,
     * so the areas of the charts are decreasing. Layout coords are top, left, bottom, right
     */
    private static final double[][][] LAYOUTS = new double[][][]{
            {{0, 0, 100, 100}},                                                                          // One chart
            {{0, 0, 100, 50}, {0, 50, 100, 100}},                                                        // Two charts
            {{0, 0, 100, 50}, {0, 50, 50, 100}, {50, 50, 100, 100}},                                     // Three charts
            {{0, 0, 50, 50}, {0, 50, 50, 100}, {50, 0, 100, 50}, {50, 50, 100, 100}},                    // Four charts
    };
    protected final BuilderOptions options;
    private String currentVisualizationID;          // ID of the main visualization (e.g. the div's ID)
    private String currentChartID;                  // Identifier for the chart we are building
    private String currentElementID;                // Identifier for the element we are building
    private StyleSheet visStyles;                   // Collection of style overrides for this visualization
    protected Controls controls;                      // Contains the controls for the current chart
    private Dataset[] datasets;                     // Data sets used by the VisItem being built

    public AbstractBuilder(BuilderOptions options) {
        this.options = options;
    }

    public final BuilderOptions getOptions() {
        return options;
    }

    /**
     * This is the entry point into building. When called, it will clear all existing build state (including control
     * and style collections) and start the build process for the defined item
     *
     * @param main   what we ant to build
     * @param width  pixel width of the rectangle into which the visualization is to be put
     * @param height pixel height of the rectangle into which the visualization is to be put
     */
    public final void build(VisItem main, int width, int height) {

        // Clear existing collections
        visStyles = new StyleSheet();

        datasets = main.getDataSets();

        // Create the main visualization area and get an identifier for it.
        currentVisualizationID = defineVisSystem(main, width, height);

        // Set the Controls to be ready for util
        controls = new Controls(currentVisualizationID);

        // The build process for each item is the same, regardless of composition method:
        // - calculate the location for it relative to the defined space
        // - build the data (giving it a an ID that is unique within the vis)
        // - build the item, which stores controls and styles, and then calls the descendant's createSingle method

        if (main.children() == null) {
            // For a single, one-element visualization, treat as a tiling of one chart
            buildTiledCharts(width, height, new VisItem[]{main.getSingle()});
        } else {
            VisTypes.Composition compositionMethod = ((VisComposition) main).method;

            if (compositionMethod == VisTypes.Composition.tile) {
                // We define a set of charts and build them, tiling them into the space.
                buildTiledCharts(width, height, main.children());
                // If we have a set of compositions, they are placed into the whole area
            } else if (compositionMethod == VisTypes.Composition.overlay) {
                double[] loc = getLocation(findFirstBounds(main));
                buildOverlayComposition(main.children(), loc);
            } else if (compositionMethod == VisTypes.Composition.inside || compositionMethod == VisTypes.Composition.nested) {
                // Nesting not yet implemented, so simply util the first element and pretend it is tiled
                buildTiledCharts(width, height, new VisItem[]{main.getSingle()});
            }

        }

        endVisSystem(main, currentVisualizationID);

    }

    public abstract String makeImports();

    private void buildOverlayComposition(VisItem[] items, double[] loc) {
        // Assemble the elements and data
        Dataset[] data = new Dataset[items.length];
        VisSingle[] elements = new VisSingle[items.length];
        for (int i = 0; i < items.length; i++) {
            elements[i] = items[i].getSingle().resolve();                       // In future, will do nesting
            data[i] = buildData(elements[i]);
        }

        currentChartID = defineChart(loc, elements, data);
        for (int i = 0; i < elements.length; i++)
            buildElement(elements[i], data[i], findElementDependedOn(elements[i], elements));
        endChart(currentChartID);
    }

    // Some elements depend on others; e.g. links need nodes to attach to
    private Integer findElementDependedOn(VisSingle vis, VisSingle[] all) {
        // To have a dependency we must have all our positions defined by keys
        if (vis.fKeys.isEmpty() || vis.positionFields().length > 0) return null;
        for (int i=0; i<all.length; i++) {
            // Find an element with a single key that we can use
            if (all[i].fKeys.size() == 1 && all[i].positionFields().length > 0)  return i;
        }
        return null;
    }

    /* Build independent charts tiled into the same display area */
    private void buildTiledCharts(int width, int height, VisItem[] charts) {
        // Count the number of unplaced charts (those without bounds definintion)
        int nUnplacedCharts = 0;
        for (VisItem chart : charts)
            if (findFirstBounds(chart) == null) nUnplacedCharts++;

        // Layout for all unplaced charts

        int unplacedCount = 0;
        for (VisItem chart : charts) {
            Param[] bounds = findFirstBounds(chart);

            double[] loc;
            if (bounds == null) {
                // No bounds are given, so use the values from the pattern
                double[][] layout = squarify(LAYOUTS[Math.min(nUnplacedCharts - 1, 3)], width, height);
                loc = layout[unplacedCount++];
            } else {
                // Bounds are given so use them
                loc = getLocation(bounds);
            }

            VisItem[] items = chart.children();
            if (items == null) {
                // The chart is a single element
                buildOverlayComposition(new VisItem[]{chart}, loc);
            } else {
                buildOverlayComposition(items, loc);
            }

        }
    }

    public String getStyleOverrides() {
        return visStyles.toString("#" + currentVisualizationID + ".brunel");
    }

    /**
     * Define the overall visualization.
     *
     * @param main   The visualization we will build into this space
     * @param width  space to put it into
     * @param height space to put it into
     * @return a non-null identifier for this visualization.
     */
    protected abstract String defineVisSystem(VisItem main, int width, int height);

    /* Swap dimensions if it makes the charts closer to the golden ration (1.62) */
    private double[][] squarify(double[][] layout, int width, int height) {
        double[][] alternate = new double[layout.length][];
        for (int i = 0; i < layout.length; i++)
            alternate[i] = new double[]{layout[i][1], layout[i][0], layout[i][3], layout[i][2]};
        return squarifyDivergence(alternate, width, height) < squarifyDivergence(layout, width, height)
                ? alternate : layout;
    }

    // Calculate the location for the bounds of the chart. We search the children and return the first one that
    // has a bounds definition
    private double[] getLocation(Param[] bounds) {
        double l = 0, t = 0, r = 100, b = 100;

        if (bounds != null && bounds.length > 0) l = bounds[0].asDouble();
        if (bounds != null && bounds.length > 1) t = bounds[1].asDouble();
        if (bounds != null && bounds.length > 2) r = bounds[2].asDouble();
        if (bounds != null && bounds.length > 3) b = bounds[3].asDouble();
        return new double[]{t, l, b, r};
    }

    private Param[] findFirstBounds(VisItem chart) {
        // Find the first item in the chart that has a bounds defined
        if (chart.children() == null) {
            return chart.getSingle().bounds;
        } else {
            for (VisItem v : chart.children()) {
                Param[] result = findFirstBounds(v);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Within the system of visualizations, this is called to define a single visualization's location.
     * It is called before we start calling the <code>buildSingle(...)</code> calls that will define the
     * contents of this area
     *
     * @param location    The chart location in percentages, relative to overall location: [top, left, bottom, right\
     * @param elements    The VisSingles that define the elements that will be created in this chart
     * @param elementData The built data for those elements
     * @return an id that identifies this visualization within the visualization system
     */
    protected abstract String defineChart(double[] location, VisSingle[] elements, Dataset[] elementData);

    /**
     * This builds the data and reports the built data to the builder
     *
     * @param vis from this we will take all the data commands and the base source data
     * @return built dataset
     */
    private Dataset buildData(VisSingle vis) {
        String constantsCommand = makeConstantsCommand(vis);
        String filterCommand = makeFilterCommands(vis);
        String binCommand = makeTransformCommands(vis);
        String summaryCommand = buildSummaryCommands(vis);
        String sortCommand = makeFieldCommands(vis.fSort);
        String seriesYFields = makeSeriesCommand(vis);
        String usedFields = required(vis.usedFields(true));

        DataTransformParameters params = new DataTransformParameters(constantsCommand,
                filterCommand, binCommand, summaryCommand, "", sortCommand, seriesYFields,
                usedFields);

        // Call the engine to see if it has any special needs
        params = modifyParameters(params, vis);

        Dataset data = vis.getDataset();                                                // The data to use
        data = data.addConstants(params.constantsCommand);                              // add constant fields
        data = data.filter(params.filterCommand);                                       // filter data
        data = data.bin(params.transformCommand);                                       // bin data
        data = data.summarize(params.summaryCommand);                                   // summarize data
        data = data.series(params.seriesCommand);                                       // convert series
        data = data.sort(params.sortCommand);                                           // sort data
        data = data.stack(params.stackCommand);                                         // stack data
        data.set("parameters", params);                                                 // Params used to build this
        return data;
    }

    private String makeSeriesCommand(VisSingle vis) {
        // Only have a series for 2+ y fields

        if (vis.fY.size() < 2) return "";
        /*
            The command is of the form:
                    y1, y2, y3; a1, a2
            Where the fields y1 ... are the fields to makes the series
            and the additional fields a1... are ones required to be kept as-is.
            #series and #values are always generated, so need to retain them additionally
        */

        LinkedHashSet<String> keep = new LinkedHashSet<String>();
        for (Param p : vis.fX) keep.add(p.asString());
        Collections.addAll(keep, vis.nonPositionFields());
        keep.remove("#series");
        keep.remove("#values");
        return Data.join(vis.fY) + ";" + Data.join(keep);
    }

    // Builds controls as needed, then the custom styles, then the visualization
    private void buildElement(VisSingle vis, Dataset data, Integer elementDependedOn) {
        try {
            // Note that controls need the ORIGINAL dataset; the one passed in has been transformed
            controls.buildControls(vis, vis.getDataset());
            currentElementID = defineElement(vis, data, indexOf(vis.getDataset(), datasets), elementDependedOn);
            if (vis.styles != null) {
                StyleSheet styles = vis.styles.replaceClass("currentElement", currentElementID);
                visStyles.add(styles, currentChartID);
            }
        } catch (Exception e) {
            throw VisException.makeBuilding(e, vis);
        }
    }

    private int indexOf(Dataset data, Dataset[] datasets) {
        for (int i = 0; i < datasets.length; i++) if (data == datasets[i]) return i;
        throw new IllegalStateException("Could not find data set in array of datasets");
    }

    /**
     * Any final work needed to finish off the vis code
     *
     * @param main
     * @param currentVisualizationID
     */
    protected abstract void endVisSystem(VisItem main, String currentVisualizationID);

    /**
     * Any final work needed to finish off the chart code
     *
     * @param currentChartID
     */
    protected abstract void endChart(String currentChartID);

    private double squarifyDivergence(double[][] rects, int width, int height) {
        double sum = 0;
        // Calculate weighted sum of divergence from the golden ratio
        for (double[] r : rects) {
            double h = Math.abs(r[1] - r[3]) * width;
            double v = Math.abs(r[0] - r[2]) * height;
            double div = (h / v - 1.62);
            sum += Math.sqrt(h * v) * div * div;
        }
        return sum;
    }

    private String makeConstantsCommand(VisSingle vis) {
        List<String> toAdd = new ArrayList<String>();
        for (String f : vis.usedFields(false)) {
            if (!f.startsWith("#") && vis.getDataset().field(f) == null) {
                // Field does not exist -- assume it is a constant and add it
                toAdd.add(f);
            }
        }
        return Data.join(toAdd, "; ");
    }

    private String makeFilterCommands(VisSingle vis) {
        List<String> commands = new ArrayList<String>();

        // All position fields must be valid -- filter if not
        String[] pos = vis.positionFields();
        for (String s : pos) {
            Field f = vis.getDataset().field(s);
            if (f == null) continue;        // May have been added as a constant -- no need to filter
            if (f.numericProperty("valid") < f.rowCount())
                commands.add(s + " valid");
        }

        for (Map.Entry<Param, String> e : vis.fTransform.entrySet()) {
            String operation = e.getValue();
            Param key = e.getKey();
            String name = getParameterFieldValue(vis, key);
            Field f = vis.getDataset().field(name);
            int N;
            if (f == null) {
                // The field must be a constant or created field -- get length from data set
                N = vis.getDataset().rowCount();
            } else {
                name = f.name;              // Make sure we use the canonical (not lax) name
                N = f.valid();              // And we can use the valid ones
            }

            if (name.equals("#row")) {
                // Invert 'top' and 'bottom' as row #1 is the top one, not the bottom
                if (operation.equals("top")) operation = "bottom";
                else if (operation.equals("bottom")) operation = "top";
            }

            int n = getParameterIntValue(key, 10);
            if (operation.equals("top"))
                commands.add(name + " ranked 1," + n);
            else if (operation.equals("bottom")) {
                commands.add(name + " ranked " + (N - n) + "," + N);
            } else if (operation.equals("inner")) {
                commands.add(name + " ranked " + n + "," + (N - n));
            } else if (operation.equals("outer")) {
                commands.add(name + " !ranked " + n + "," + (N - n));
            }
        }

        return Data.join(commands, "; ");
    }

    private String getParameterFieldValue(VisSingle vis, Param param) {

        if (param != null && param.isField()) {
            // Usual case of a field specified
            return param.asField();
        } else {
            // Try Y fields then aesthetic fields
            if (vis.fY.size() == 1) {
                String s = vis.fY.get(0).asField();
                if (!s.startsWith("'") && !s.startsWith("#")) return s;       // If it's a real field
            }
            if (vis.aestheticFields().length > 0) return vis.aestheticFields()[0];
            return "#row";      // If all else fails
        }
    }

    protected int getParameterIntValue(Param param, int defaultValue) {
        if (param == null) return defaultValue;
        if (param.isField()) {
            // The parameter is a field, so we examine the modifier for the int value
            return getParameterIntValue(param.firstModifier(), defaultValue);
        } else {
            // The parameter is a value
            return (int) param.asDouble();
        }
    }

    private String makeFieldCommands(List<Param> params) {
        String[] commands = new String[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Param p = params.get(i);
            String s = p.asField();
            if (p.hasModifiers())
                commands[i] = s + ":" + p.firstModifier().asString();
            else
                commands[i] = s;
        }
        return Data.join(commands, "; ");
    }

    private String buildSummaryCommands(VisSingle item) {
        Map<String, String> spec = new HashMap<String, String>();

        // We must account for all of these except for the special fields series and values
        // As they will be handled later
        HashSet<String> fields = new HashSet<String>(Arrays.asList(item.usedFields(false)));
        fields.remove("#series");
        fields.remove("#values");

        // Add the summary measures
        for (Map.Entry<Param, String> e : item.fSummarize.entrySet()) {
            Param p = e.getKey();
            String name = p.asField();
            String measure = e.getValue();
            if (p.hasModifiers()) measure += ":" + p.firstModifier().asString();
            spec.put(name, name + ":" + measure);
            fields.remove(name);
        }

        // Add all color used fields in as dimensions (factors)
        for (String s : fields) {
            // Count is an implicit summary
            if (s.equals("#count"))
                spec.put(s, s + ":sum");
            else
                spec.put(s, s);
        }

        // X fields are used for the percentage bases
        for (Param s : item.fX) spec.put(s.asField(), s.asField() + ":base");

        // Return null if summary is not called for
        if (spec.containsKey("#count") || item.fSummarize.size() > 0) {
            String[] result = new String[spec.size()];
            int n = 0;
            for (Map.Entry<String, String> e : spec.entrySet())
                result[n++] = e.getKey() + "=" + e.getValue();
            return Data.join(result, "; ");
        } else
            return "";
    }

    /*
        Builds the command to transform fields without summarizing -- ranks, bin, inside and outside
        The commands look like this:
            salary=bin:10; age=rank; education=outside:90
     */
    private String makeTransformCommands(VisSingle item) {
        if (item.fTransform.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Param, String> e : item.fTransform.entrySet()) {
            Param p = e.getKey();
            String name = p.asField();
            String measure = e.getValue();
            if (measure.equals("bin") || measure.equals("rank")) {
                if (p.hasModifiers()) measure += ":" + p.firstModifier().asString();
                if (b.length() > 0) b.append("; ");
                b.append(name).append("=").append(measure);
            }
        }
        return b.toString();
    }

    private String required(String[] fields) {
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, fields);
        // ensure we always have #row and #count
        if (!result.contains("#row")) result.add("#row");
        if (!result.contains("#count")) result.add("#count");
        return Data.join(result, "; ");
    }

    /* Do any further processing and return the transformed data set */
    protected abstract DataTransformParameters modifyParameters(DataTransformParameters params, VisSingle vis);

    /* Adds an 'element' in GoG terms -- a bar, line, point, etc. */
    protected abstract String defineElement(VisSingle vis, Dataset data, int datasetIndex, Integer elementDependedOn);

}
