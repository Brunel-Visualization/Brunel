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
import org.brunel.build.controls.Controls;
import org.brunel.build.data.DataTableWriter;
import org.brunel.build.info.ChartLayout;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisComposition;
import org.brunel.model.VisElement;
import org.brunel.model.VisItem;
import org.brunel.model.VisTypes;

import java.util.List;

/**
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
public class VisualizationBuilder {

	private static final String COPYRIGHT_COMMENTS = "\t<!--\n" +
			"\t\tD3 Copyright \u00a9 2012, Michael Bostock\n" +
			"\t\tjQuery Copyright \u00a9 2010 by The jQuery Project\n" +
			"\t\tsumoselect Copyright \u00a9 2014 Hemant Negi\n " +
			"\t-->\n";

	/**
	 * Return the required builder with default options
	 */
	public static VisualizationBuilder make() {
		return make(new BuilderOptions());
	}

	/**
	 * Return the required builder with the stated options
	 */
	public static VisualizationBuilder make(BuilderOptions options) {
		return new VisualizationBuilder(options);
	}

	private final BuilderOptions options;        // How to build
	private VisInfo visStructure;                // Information on the main structure
	private ScriptWriter out;                // Where to write code

	private VisualizationBuilder(BuilderOptions options) {
		this.options = options;
	}

	/**
	 * Builds a visualization
	 *
	 * @param main   the description of the visualization to build
	 * @param width  pixel width of the rectangle into which the visualization is to be put
	 * @param height pixel height of the rectangle into which the visualization is to be put
	 */
	public final void build(VisItem main, int width, int height) {
		this.visStructure = new VisInfo(width, height, options);

		// Index the datasets with the number in the list of input data sets
		Dataset[] datasets = main.getDataSets();
		for (int i = 0; i < datasets.length; i++) datasets[i].set("index", i);

		// Create the main visualization area
		writeStart();

		// The build process for each item is the same, regardless of composition method:
		// - calculate the location for it relative to the defined space
		// - build the data (giving it a an ID that is unique within the vis)
		// - build the item, which stores controls and styles, and then calls the descendant's createSingle method

		VisItem[] children = main.children();
		if (children == null) {
			// For a single, one-element visualization, treat as a tiling of one chart
			buildTiledCharts(width, height, new VisItem[]{main.getSingle()});
		} else {
			VisTypes.Composition compositionMethod = ((VisComposition) main).method;

			if (compositionMethod == VisTypes.Composition.tile) {
				// We define a set of charts and build them, tiling them into the space.
				buildTiledCharts(width, height, children);
			} else if (compositionMethod == VisTypes.Composition.overlay) {
				// If we have a set of compositions, they are placed into the whole area
				// Each is an individual element
				double[] loc = new ChartLayout(width, height, main).getLocation(0);
				new ChartBuilder(visStructure, options, loc, out).build(0, asSingle(children));
			} else if (compositionMethod == VisTypes.Composition.inside || compositionMethod == VisTypes.Composition.nested) {
				buildNestedChart(width, height, children);
			}
		}

		writeEnd(main);
	}

	private VisElement[] asSingle(VisItem[] children) {
		VisElement[] items = new VisElement[children.length];
		for (int i = 0; i < items.length; i++) items[i] = (VisElement) children[i];
		return items;
	}

	public Controls getControls() {
		return visStructure.controls;
	}

	public int getHeight() {
		return visStructure.height;
	}

	public int getWidth() {
		return visStructure.width;
	}

	public final BuilderOptions getOptions() {
		return options;
	}

	/**
	 * Some visualizations may re-define or add to the standard styles. This will be a CSS-compatible
	 * set of style definitions. It will be suitable for placing within a HTML <code>style</code> section.
	 * The styles will all be scoped to affect only <code>brunel</code> classes and (if required) the
	 * correct chart within the visualization system.
	 *
	 * @return non-null, but possibly empty CSS styles definition
	 */
	public String getStyleOverrides() {
		return visStructure.visStyles.toString("#" + options.visIdentifier + ".brunel");
	}

	public String getVisualization() {
		return out.content();
	}

	public String makeImports() {
		Controls controls = getControls();

		String pattern = "\t<script src=\"%s\" charset=\"utf-8\"></script>\n";

		String base = COPYRIGHT_COMMENTS +
				String.format(pattern, BuilderOptions.fullLocation(options.locD3))
				+ String.format(pattern, BuilderOptions.fullLocation(options.locBidi))
				+ String.format(pattern, BuilderOptions.fullLocation(options.locTopoJson));

		if (controls.isNeeded()) {
			base = base + String.format(pattern, "http://code.jquery.com/jquery-1.10.2.js")
					+ String.format(pattern, "http://code.jquery.com/ui/1.11.4/jquery-ui.js");
		}

		if (options.locJavaScript.startsWith("file")) {
			base = base
					+ String.format(pattern, options.locJavaScript + "/BrunelData.js")
					+ String.format(pattern, options.locJavaScript + "/BrunelD3.js")
					+ String.format(pattern, options.locJavaScript + "/BrunelBidi.js");
			if (controls.isNeeded()) base = base
					+ String.format(pattern, options.locJavaScript + "/BrunelEventHandlers.js")
					+ String.format(pattern, options.locJavaScript + "/BrunelJQueryControlFactory.js")
					+ String.format(pattern, options.locJavaScript + "/sumoselect/jquery.sumoselect.min.js");
		} else {
			base = base + String.format(pattern, options.locJavaScript + "/brunel." + options.version + ".min.js");
			if (controls.isNeeded())
				base = base + String.format(pattern, options.locJavaScript + "/brunel.controls." + options.version + ".min.js");
		}
		return base;
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

	private void buildNestedChart(int width, int height, VisItem[] children) {
		// The following rules should be ensured by the parser
		if (children.length != 2)
			throw new IllegalStateException("Nested charts only implemented for exactly one inner, one outer");
		if (children[0].children() != null)
			throw new IllegalStateException("Inner chart in nesting must be atomic");
		if (children[1].children() != null)
			throw new IllegalStateException("Outer chart in nesting must be atomic");

		VisElement inner = children[1].getSingle();
		VisElement outer = children[0].getSingle();

		// For now, just deal with simple case of two charts, 0 and 1
		visStructure.nesting.put(1, 0);
		double[] loc = new ChartLayout(width, height, outer).getLocation(0);
		ChartStructure outerStructure = new ChartBuilder(visStructure, options, loc, out).buildNestedOuter(0, 1, outer);
		loc = new ChartLayout(width, height, inner).getLocation(0);
		new ChartBuilder(visStructure, options, loc, out).buildNestedInner(1, outerStructure, inner);
	}

	/* Build independent charts tiled into the same display area */
	private void buildTiledCharts(int width, int height, VisItem[] charts) {
		ChartLayout layout = new ChartLayout(width, height, charts);

		for (int i = 0; i < charts.length; i++) {
			VisItem chart = charts[i];
			VisItem[] items = chart.children();

			ChartBuilder chartBuilder = new ChartBuilder(visStructure, options, layout.getLocation(i), out);
			if (items == null) {
				// The chart is a single element
				chartBuilder.build(i, (VisElement) chart);
			} else {
				VisTypes.Composition compositionMethod = ((VisComposition) chart).method;
				if (compositionMethod == VisTypes.Composition.inside || compositionMethod == VisTypes.Composition.nested) {
					buildNestedChart(width, height, items);
				} else {
					chartBuilder.build(i, asSingle(items));
				}
			}
		}

	}

	private void writeStart() {
		this.out = new ScriptWriter(options);

		// Write the class definition function (and flag to use strict mode)
		out.add("function ", options.className, "(visId) {").ln().indentMore();
		out.add("\"use strict\";").comment("Strict mode");

		// Add commonly used definitions
		out.add("var datasets = [],").comment("Array of datasets for the original data");
		out.add("    pre = function(d, i) { return d },").comment("Default pre-process does nothing");
		out.add("    post = function(d, i) { return d },").comment("Default post-process does nothing");
		out.add("    transitionTime = 200,").comment("Transition time for animations");
		out.add("    charts = [],").comment("The charts in the system");
		out.add("    vis = d3.select('#' + visId).attr('class', 'brunel');").comment("the SVG container");

		out.ln().add("BrunelD3.addDefinitions(vis);").comment("ensure standard symbols present");
	}

	private void writeEnd(VisItem main) {

		// Define how we set the data into the system
		out.add("function setData(rowData, i) { datasets[i||0] = BrunelD3.makeData(rowData) }").ln();

		// Define the update functions
		if (visStructure.nesting.isEmpty()) {
			// For no nesting, it's easy
			out.add("function updateAll(time) { charts.forEach(function(x) {x.build(time || 0)}) }").ln();
		} else {
			// For nesting, need a custom update method
			// TODO make work for more than two charts
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
		new DataTableWriter(main, visStructure.allElements, out, options).write();

		// Call the function on the data
		if (options.generateBuildCode) {
			out.titleComment("Call Code to Build the system");
			out.add("var " + options.visObject, " = new", options.className, "(" + out.quote(options.visIdentifier) + ")").endStatement();

			//Initialize and wire any events that may be needed for controls.
			//This must be done prior to building the visualization so defaults can be set.
			visStructure.controls.writeEventHandler(out, options.visObject);

			int length = main.getDataSets().length;

			int enterAnimateTime = enterAnimate(main, length);
			if (enterAnimateTime > 0) {
				out.add("BrunelD3.animateBuild(v,", String.format(options.dataName, 1),
						",", enterAnimateTime, ")").endStatement();
			} else {
				out.add(options.visObject + ".build(");
				for (int i = 0; i < length; i++) {
					if (i > 0) out.add(", ");
					out.add(String.format(options.dataName, i + 1));
				}
				out.add(")").endStatement();
				writeBidiEnd(main);
			}
		}

		// Add controls code
		visStructure.controls.writeControls(out, options.visObject);

	}
	
	private void writeBidiEnd(VisItem main) {
		if (! (main instanceof VisElement)) 
			return;

		if (((VisElement)main).fLocale == null 
				&& ((VisElement)main).fTextDir == null 
				&& ((VisElement)main).fGuiDir == null 
				&& ((VisElement)main).fNumShape == null 
				)
			return;

		out.add("var BrunelD3Locale;\n");
		out.add("bidiProcessing('" + ((VisElement)main).fLocale + "', '" + ((VisElement)main).fTextDir + "', '"
				+ ((VisElement)main).fGuiDir + "', '" + ((VisElement)main).fNumShape + "');");

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

}
