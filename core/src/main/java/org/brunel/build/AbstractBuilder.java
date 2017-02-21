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

import org.brunel.build.controls.Controls;
import org.brunel.build.data.DataBuilder;
import org.brunel.build.data.DataModifier;
import org.brunel.build.info.ChartLayout;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Dataset;
import org.brunel.model.VisComposition;
import org.brunel.model.VisException;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Composition;
import org.brunel.model.style.StyleSheet;

import java.util.HashMap;
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
public abstract class AbstractBuilder implements Builder, DataModifier {

    protected final BuilderOptions options;
    protected Controls controls;                    // Contains the controls for the current chart
    private StyleSheet visStyles;                   // Collection of style overrides for this visualization
    private Dataset[] datasets;                     // datasets used by this visualization

    protected final Map<Integer, Integer> nesting;    // Which charts are nested within which other ones

    public AbstractBuilder(BuilderOptions options) {
        this.options = options;
        nesting = new HashMap<>();
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

        // Clear existing collections and prepare for new controls
        visStyles = new StyleSheet();
        controls = new Controls(options);
        datasets = main.getDataSets();

        // Create the main visualization area
        defineVisSystem(main, width, height);

        // The build process for each item is the same, regardless of composition method:
        // - calculate the location for it relative to the defined space
        // - build the data (giving it a an ID that is unique within the vis)
        // - build the item, which stores controls and styles, and then calls the descendant's createSingle method

        VisItem[] children = main.children();
        if (children == null) {
            // For a single, one-element visualization, treat as a tiling of one chart
            buildTiledCharts(width, height, new VisItem[]{main.getSingle()});
        } else {
            Composition compositionMethod = ((VisComposition) main).method;

            if (compositionMethod == Composition.tile) {
                // We define a set of charts and build them, tiling them into the space.
                buildTiledCharts(width, height, children);
            } else if (compositionMethod == Composition.overlay) {
                // If we have a set of compositions, they are placed into the whole area
                double[] loc = new ChartLayout(width, height, main).getLocation(0);
                buildSingleChart(0, children, loc, null, null);
            } else if (compositionMethod == Composition.inside || compositionMethod == Composition.nested) {
                buildNestedChart(width, height, children);
            }

        }

        endVisSystem(main);
    }

    private void buildNestedChart(int width, int height, VisItem[] children) {
        // The following rules should be ensured by the parser
        if (children.length != 2)
            throw new IllegalStateException("Nested charts only implemented for exactly one inner, one outer");
        if (children[0].children() != null)
            throw new IllegalStateException("Inner chart in nesting must be atomic");
        if (children[1].children() != null)
            throw new IllegalStateException("Outer chart in nesting must be atomic");

        VisSingle inner = children[1].getSingle();
        VisSingle outer = children[0].getSingle();

        // For now, just deal with simple case of two charts, 0 and 1
        nesting.put(1, 0);
        double[] loc = new ChartLayout(width, height, outer).getLocation(0);
        ChartStructure outerStructure = buildSingleChart(0, new VisItem[]{outer}, loc, null, 1);
        loc = new ChartLayout(width, height, inner).getLocation(0);
        buildSingleChart(1, new VisItem[]{inner}, loc, outerStructure, null);
    }

    public final BuilderOptions getOptions() {
        return options;
    }

    public String getStyleOverrides() {
        return visStyles.toString("#" + options.visIdentifier + ".brunel");
    }

    public abstract String makeImports();

    /**
     * Within the system of visualizations, this is called to define a single visualization's location.
     * It is called before we start calling the <code>buildSingle(...)</code> calls that will define the
     * contents of this area
     *
     * @param structure
     * @param location  The chart location in percentages, relative to overall location: [top, left, bottom, right\
     */
    protected abstract void defineChart(ChartStructure structure, double[] location);

    /* Adds an 'element' in GoG terms -- a bar, line, point, etc. */
    protected abstract void defineElement(ElementStructure structure);

    /**
     * Define the overall visualization.
     *
     * @param main   The visualization we will build into this space
     * @param width  space to put it into
     * @param height space to put it into
     */
    protected abstract void defineVisSystem(VisItem main, int width, int height);

    /**
     * Any final work needed to finish off the chart code
     *
     * @param structure
     */
    protected abstract void endChart(ChartStructure structure);

    /**
     * Any final work needed to finish off the vis code
     *
     * @param main
     */
    protected abstract void endVisSystem(VisItem main);

    // Builds controls as needed, then the custom styles, then the visualization
    private void buildElement(ElementStructure structure) {
        try {
            // Note that controls need the ORIGINAL dataset; the one passed in has been transformed

        	//The index of the dataset containing the field to filter
        	int datasetIndex = structure.chart.getBaseDatasetIndex(structure.vis.getDataset());

            controls.buildControls(structure.vis, structure.vis.getDataset(), datasetIndex);

            defineElement(structure);
            if (structure.vis.styles != null) {
                StyleSheet styles = structure.vis.styles.replaceClass("currentElement", "element" + structure.elementID());
                visStyles.add(styles, "chart" + structure.chart.chartID());
            }
        } catch (Exception e) {
            throw VisException.makeBuilding(e, structure.vis);
        }
    }

    private ChartStructure buildSingleChart(int chartIndex, VisItem[] items, double[] loc, ChartStructure outer, Integer innerChartIndex) {

        // Assemble the elements and data
        Dataset[] data = new Dataset[items.length];
        VisSingle[] elements = new VisSingle[items.length];
        for (int i = 0; i < items.length; i++) {
            elements[i] = items[i].getSingle().makeCanonical();
            data[i] = new DataBuilder(elements[i], this).build();

        }

        ChartStructure structure = new ChartStructure(chartIndex, elements, data, datasets, outer, innerChartIndex, options.visIdentifier);
        structure.accessible = options.accessibleContent;

        defineChart(structure, loc);
        for (ElementStructure e : structure.elementStructure) buildElement(e);
        endChart(structure);
        return structure;
    }

    /* Build independent charts tiled into the same display area */
    private void buildTiledCharts(int width, int height, VisItem[] charts) {
        ChartLayout layout = new ChartLayout(width, height, charts);

        for (int i = 0; i < charts.length; i++) {
            VisItem chart = charts[i];
            double[] loc = layout.getLocation(i);
            VisItem[] items = chart.children();

            if (items == null) {
                // The chart is a single element
                buildSingleChart(i, new VisItem[]{chart}, loc, null, null);
            } else {
                Composition compositionMethod = ((VisComposition) chart).method;
                if (compositionMethod == Composition.inside || compositionMethod == Composition.nested) {
                    buildNestedChart(width, height, items);
                } else {
                    buildSingleChart(i, items, loc, null, null);
                }
            }
        }

    }

}
