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

package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.chart.ChartStructure;
import org.brunel.build.controls.Controls;
import org.brunel.build.data.DataBuilder;
import org.brunel.build.data.DataModifier;
import org.brunel.build.element.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Dataset;
import org.brunel.model.VisComposition;
import org.brunel.model.VisException;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;
import org.brunel.model.style.StyleSheet;

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
public abstract class AbstractBuilder implements Builder, DataModifier {

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
    private StyleSheet visStyles;                   // Collection of style overrides for this visualization
    protected Controls controls;                    // Contains the controls for the current chart
    protected ChartStructure structure;             // Structure of the chart currently being built

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

        // Create the main visualization area
        defineVisSystem(main, width, height);

        // Set the Controls to be ready for util
        controls = new Controls(options);

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
                buildSingleChart(main, 0, main.children(), loc);
            } else if (compositionMethod == VisTypes.Composition.inside || compositionMethod == VisTypes.Composition.nested) {
                // Nesting not yet implemented, so simply util the first element and pretend it is tiled
                buildTiledCharts(width, height, new VisItem[]{main.getSingle()});
            }

        }

        endVisSystem(main);
    }

    public abstract String makeImports();

    private void buildSingleChart(VisItem chart, int chartIndex, VisItem[] items, double[] loc) {

        // Assemble the elements and data
        Dataset[] data = new Dataset[items.length];
        VisSingle[] elements = new VisSingle[items.length];
        for (int i = 0; i < items.length; i++) {
            elements[i] = items[i].getSingle().resolve();                               // In future, will do nesting
            data[i] = new DataBuilder(elements[i], this).build();
        }

        this.structure = new ChartStructure(chart, chartIndex, elements, data);         // Characterize inter-element dependency
        defineChart(structure, loc);
        for (int i = 0; i < elements.length; i++) {
            buildElement(structure.elementStructure[i]);
        }
        endChart(structure);
    }

    /* Build independent charts tiled into the same display area */
    private void buildTiledCharts(int width, int height, VisItem[] charts) {
        // Count the number of unplaced charts (those without bounds definition)
        int nUnplacedCharts = 0;
        for (VisItem chart : charts)
            if (findFirstBounds(chart) == null) nUnplacedCharts++;

        // Layout for all unplaced charts

        int unplacedCount = 0;
        for (int i = 0; i < charts.length; i++) {
            VisItem chart = charts[i];
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
                buildSingleChart(chart, i, new VisItem[]{chart}, loc);
            } else {
                buildSingleChart(chart, i, items, loc);
            }

        }
    }

    public String getStyleOverrides() {
        return visStyles.toString("#" + options.visIdentifier + ".brunel");
    }

    /**
     * Define the overall visualization.
     *
     * @param main   The visualization we will build into this space
     * @param width  space to put it into
     * @param height space to put it into
     */
    protected abstract void defineVisSystem(VisItem main, int width, int height);

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
     * @param structure
     * @param location  The chart location in percentages, relative to overall location: [top, left, bottom, right\
     */
    protected abstract void defineChart(ChartStructure structure, double[] location);

    // Builds controls as needed, then the custom styles, then the visualization
    private void buildElement(ElementStructure structure) {
        try {
            // Note that controls need the ORIGINAL dataset; the one passed in has been transformed
            controls.buildControls(structure.vis, structure.vis.getDataset());
            defineElement(structure);
            if (structure.vis.styles != null) {
                StyleSheet styles = structure.vis.styles.replaceClass("currentElement", structure.getElementID());
                visStyles.add(styles, structure.chartStructure.getChartID());
            }
        } catch (Exception e) {
            throw VisException.makeBuilding(e, structure.vis);
        }
    }

    /**
     * Any final work needed to finish off the vis code
     *  @param main
     *
     */
    protected abstract void endVisSystem(VisItem main);

    /**
     * Any final work needed to finish off the chart code
     *
     * @param dependency
     */
    protected abstract void endChart(ChartStructure dependency);

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

    /* Adds an 'element' in GoG terms -- a bar, line, point, etc. */
    protected abstract void defineElement(ElementStructure structure);

}
