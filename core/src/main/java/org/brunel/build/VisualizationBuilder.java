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
import org.brunel.build.data.DataTransformWriter;
import org.brunel.build.data.TransformedData;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.info.ChartLayout;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.titles.ChartTitleBuilder;
import org.brunel.build.util.Accessibility;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.SVGGroupUtility;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisComposition;
import org.brunel.model.VisException;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.style.StyleSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	private final BuilderOptions options;
	private final Map<Integer, Integer> nesting;    	// Which charts are nested within which other ones
	private Controls controls;                        	// Contains the controls for the current chart

	private ScriptWriter out;                        	// Where to write code
	public int visWidth, visHeight;                    	// Overall vis size
	private ScaleBuilder scalesBuilder;                	// The scales for the current chart
	private ElementBuilder[] elementBuilders;        	// Builder for each element
	private StyleSheet visStyles;                   	// Collection of style overrides for this visualization
	private Set<ElementStructure> allElements;      	// Collection of all elements used

	private VisualizationBuilder(BuilderOptions options) {
		this.options = options;
		nesting = new HashMap<>();
	}

	/**
	 * Builds the visualization
	 *
	 * @param main   the description of the visualization to build
	 * @param width  pixel width of the rectangle into which the visualization is to be put
	 * @param height pixel height of the rectangle into which the visualization is to be put
	 */
	public final void build(VisItem main, int width, int height) {

		// Clear existing collections and prepare for new items
		visStyles = new StyleSheet();
		controls = new Controls(options);
		allElements = new LinkedHashSet<>();

		// Index the datasets with the number in the list of input data sets
		Dataset[] datasets = main.getDataSets();
		for (int i = 0; i < datasets.length; i++) datasets[i].set("index", i);

		// Create the main visualization area
		defineVisSystem(width, height);

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
				double[] loc = new ChartLayout(width, height, main).getLocation(0);
				buildSingleChart(0, children, loc, null, null);
			} else if (compositionMethod == VisTypes.Composition.inside || compositionMethod == VisTypes.Composition.nested) {
				buildNestedChart(width, height, children);
			}

		}

		endVisSystem(main);
	}

	/**
	 * Returns the options used for building the visualization
	 *
	 * @return options used
	 */
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
		return visStyles.toString("#" + options.visIdentifier + ".brunel");
	}

	public String getVisualization() {
		return out.content();
	}

	public String makeImports() {

		String pattern = "\t<script src=\"%s\" charset=\"utf-8\"></script>\n";

		String base = COPYRIGHT_COMMENTS +
				String.format(pattern, BuilderOptions.fullLocation(options.locD3))
				+ String.format(pattern, BuilderOptions.fullLocation(options.locTopoJson));

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

	private void defineChart(ChartStructure structure, double[] location) {

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
				.onNewLine().add("elements = [];").comment("Array of elements in this chart")
				.indentLess();

		// Transpose if needed
		if (forceSquare(structure.elements)) out.add("geom.makeSquare()").endStatement();
		if (scalesBuilder.coords == Coordinates.transposed) out.add("geom.transpose()").endStatement();

		// Now build the main groups
		out.titleComment("Define groups for the chart parts");
		writeMainGroups(structure);
		for (ElementBuilder builder : elementBuilders) builder.writePerChartDefinitions();

		title.writeContent("chart", out);
		sub.writeContent("chart", out);

		if (structure.diagram == null) {
			// Scales and axes
			out.titleComment("Scales");
			scalesBuilder.writeCoordinateScales();

			// Define the Axes
			if (scalesBuilder.needsAxes()) {
				out.titleComment("Axes");
				scalesBuilder.writeAxes();
			}
		} else if (structure.diagram != VisTypes.Diagram.parallel) {
			// Parallel coordinates handles it differently
			scalesBuilder.writeDiagramScales();
		}

		// Attach the zoom
		structure.interaction.addZoomFunctionality(out);

		// Symbols need to be added to the svg definitions block
		structure.symbols.addDefinitions(out);
	}

	// Builds controls as needed, then the custom styles, then the visualization
	private void buildElement(ElementStructure structure) {
		try {

			controls.buildControls(structure);			// build controls
			defineElement(structure);          			// define the element
			if (structure.vis.styles != null) {
				// we need to add these to the main style sheet with correct element class identifier
				StyleSheet styles = structure.vis.styles.replaceClass("currentElement", "element" + structure.elementID());
				visStyles.add(styles, "chart" + structure.chart.chartID());
			}
			allElements.add(structure);                	// store the built data
		} catch (Exception e) {
			throw VisException.makeBuilding(e, structure.vis);
		}
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

	private ChartStructure buildSingleChart(int chartIndex, VisItem[] items, double[] loc, ChartStructure outer, Integer innerChartIndex) {

		// Assemble the elements and data
		TransformedData[] data = new TransformedData[items.length];
		VisSingle[] elements = new VisSingle[items.length];
		for (int i = 0; i < items.length; i++) {
			elements[i] = items[i].getSingle().makeCanonical();
			data[i] = TransformedData.make(elements[i]);
		}

		ChartStructure structure = new ChartStructure(chartIndex, elements, data, outer, innerChartIndex, options.visIdentifier);
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
				VisTypes.Composition compositionMethod = ((VisComposition) chart).method;
				if (compositionMethod == VisTypes.Composition.inside || compositionMethod == VisTypes.Composition.nested) {
					buildNestedChart(width, height, items);
				} else {
					buildSingleChart(i, items, loc, null, null);
				}
			}
		}

	}

	private boolean forceSquare(VisSingle[] elements) {
		for (VisSingle e : elements) {
			for (Param p : e.fCoords) {
				if (p.asString().equals("square")) return true;
			}
		}
		return false;
	}

	private void createBuilders(ChartStructure structure, double[] chartMargins) {
		// Define scales
		double chartWidth = visWidth - chartMargins[1] - chartMargins[3];
		double chartHeight = visHeight - chartMargins[0] - chartMargins[2];
		structure.setExtent((int) chartWidth, (int) chartHeight);
		this.scalesBuilder = new ScaleBuilder(structure, out);

		ElementStructure[] structures = structure.elementStructure;
		elementBuilders = new ElementBuilder[structures.length];
		for (int i = 0; i < structures.length; i++)
			elementBuilders[i] = ElementBuilder.make(structures[i], out, scalesBuilder);
	}

	private void defineElement(ElementStructure structure) {

		ElementBuilder elementBuilder = elementBuilders[structure.index];

		out.titleComment("Define element #" + structure.elementID());
		out.add("elements[" + structure.index + "] = function() {").indentMore();
		out.onNewLine().add("var original, processed,").comment("data sets passed in and then transformed")
				.indentMore()
				.onNewLine().add("element, data,").comment("Brunel element information and brunel data")
				.onNewLine().add("selection, merged;").comment("D3 selection and merged selection")
				.indentLess();

		// Add data variables used throughout
		addElementGroups(elementBuilder, structure);

		// Write the data transforms
		DataTransformWriter dataBuilder = new DataTransformWriter(structure, out);
		dataBuilder.writeDataManipulation();

		scalesBuilder.writeAestheticScales(structure);
		scalesBuilder.writeLegends(structure.vis);

		elementBuilder.preBuildDefinitions();

		// Main method to make a vis
		out.titleComment("Build element from data");

		out.add("function build(transitionMillis) {").ln().indentMore();
		elementBuilder.generate();
		structure.chart.interaction.addHandlers(structure, out);

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
		addElementExports(structure.vis, dataBuilder, structure);

		out.indentLess().onNewLine().add("}()").endStatement().ln();
	}

	private void defineVisSystem(int width, int height) {
		this.visWidth = width;
		this.visHeight = height;
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

	private void endChart(ChartStructure structure) {
		out.onNewLine().add("function build(time, noData) {").indentMore();

		out.onNewLine().add("var first = elements[0].data() == null").endStatement();
		out.add("if (first) time = 0;").comment("No transition for first call");

		// For coordinate system charts, see if axes are needed
		if (scalesBuilder.needsAxes() || structure.geo != null && structure.geo.withGraticule)
			out.onNewLine().add("buildAxes(time)").endStatement();

		Integer[] order = structure.elementBuildOrder();

		out.onNewLine().add("if ((first || time > -1) && !noData)");
		if (order.length > 1) {
			out.add("{").indentMore();
			for (int i : order)
				out.onNewLine().add("elements[" + i + "].makeData()").endStatement();
			out.indentLess().onNewLine().add("}").ln();
			if (structure.diagram == VisTypes.Diagram.network) {
				out.onNewLine().add("graph = null").endStatement();
			}
		} else {
			out.add("elements[0].makeData()").endStatement();
		}
		for (int i : order)
			out.onNewLine().add("elements[" + i + "].build(time);");

		for (ElementBuilder builder : elementBuilders) builder.writeBuildCommands();

		out.indentLess().onNewLine().add("}").ln();

		out.ln().comment("Expose the following components of the chart");
		out.add("return {").indentMore()
				.onNewLine().add("elements : elements,")
				.onNewLine().add("interior : interior,");

		if (structure.diagram == null) {
			out.onNewLine().add("scales: {x:scale_x, y:scale_y},");
		}

		structure.interaction.defineChartZoomFunction(out);

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

	private void endVisSystem(VisItem main) {

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
		new DataTableWriter(main, allElements, out, options).write();

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

	private void addElementGroups(ElementBuilder builder, ElementStructure structure) {
		String elementTransform = makeElementTransform(scalesBuilder.coords);

		// The overall group for this element, with accessibility and transforms
		out.add("var elementGroup = interior.append('g').attr('class', 'element" + structure.elementID() + "')");
		Accessibility.addElementInformation(structure, out);
		if (elementTransform != null) out.addChained(elementTransform);

		// The main group
		out.add(",").ln().indent().add("main = elementGroup.append('g').attr('class', 'main')");

		// The group for labels
		out.add(",").ln().indent()
				.add("labels = BrunelD3.undoTransform(elementGroup.append('g')")
				.add(".attr('class', 'labels').attr('aria-hidden', 'true'), elementGroup)");

		// Any extra groups needed (diagrams mostly do this)
		builder.addAdditionalElementGroups();

		out.endStatement();
	}

	private void addElementExports(VisSingle vis, DataTransformWriter dataBuilder, ElementStructure structure) {
		out.add("return {").indentMore();
		out.onNewLine().add("data:").at(24).add("function() { return processed },");
		out.onNewLine().add("original:").at(24).add("function() { return original },");
		out.onNewLine().add("internal:").at(24).add("function() { return data },");
		out.onNewLine().add("selection:").at(24).add("function() { return merged },");
		out.onNewLine().add("makeData:").at(24).add("makeData,");
		out.onNewLine().add("build:").at(24).add("build,");
		out.onNewLine().add("chart:").at(24).add("function() { return charts[" + structure.chart.chartIndex + "] },");
		out.onNewLine().add("group:").at(24).add("function() { return elementGroup },");
		out.onNewLine().add("fields: {").indentMore();

		boolean needsComma = writeFieldName("x", vis.fX, false);
		if (vis.fRange != null)
			needsComma = writeFieldName("y", Arrays.asList(vis.fRange), needsComma);
		else
			needsComma = writeFieldName("y", vis.fY, needsComma);

		List<String> keys = dataBuilder.makeKeyFields();
		if (!keys.isEmpty()) {
			needsComma = writeFieldName("key", keys, needsComma);
		}

		needsComma = writeFieldName("color", vis.fColor, needsComma);
		needsComma = writeFieldName("size", vis.fSize, needsComma);
		needsComma = writeFieldName("opacity", vis.fOpacity, needsComma);
		needsComma = writeFieldName("class", vis.fCSS, needsComma);
		writeFieldName("symbol", vis.fSymbol, needsComma);
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

	/**
	 * Write a set of field names as properties
	 *
	 * @param key              property key
	 * @param fieldNames       list of names to write
	 * @param needsCommaBefore true if a comma needs to be written (it is part of a list)
	 * @return updated needsCommaBefore, changed to be true if we added anthing
	 */
	private boolean writeFieldName(String key, List fieldNames, boolean needsCommaBefore) {
		if (fieldNames.isEmpty()) return needsCommaBefore;
		if (needsCommaBefore) out.add(",");
		List<String> names = new ArrayList<>();
		for (Object p : fieldNames) {
			if (p instanceof Param) names.add(((Param) p).asField());
			else names.add(p.toString());
		}
		out.onNewLine().add(key, ":").at(24).add("[").addQuotedCollection(names).add("]");
		return true;
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

		// Only write group info if we have multiple elements within the chart
		if (structure.elements.length > 1) groupUtil.addAccessibleChartInfo();

		out.addChained(makeTranslateTransform("geom.chart_left", "geom.chart_top")).endStatement();

		structure.interaction.addOverlayForZoom(structure.diagram, out);

		out.add("chart.append('rect').attr('class', 'background')")
				.add(".attr('width', geom.chart_right-geom.chart_left).attr('height', geom.chart_bottom-geom.chart_top)")
				.endStatement();

		String axesTransform = makeTranslateTransform("geom.inner_left", "geom.inner_top");

		// Note we write the initial zoom level of "None" in here
		out.add("var interior = chart.append('g').attr('class', 'interior zoomNone')")
				.addChained(axesTransform);

		// Nested charts do not need additional clipping
		if (!structure.nested()) groupUtil.addClipPathReference("inner");

		out.endStatement();
		out.add("interior.append('rect').attr('class', 'inner')")
				.add(".attr('width', geom.inner_width).attr('height', geom.inner_height)")
				.endStatement();
		out.add("var gridGroup = interior.append('g').attr('class', 'grid')")
				.endStatement();

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

}
