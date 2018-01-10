package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.data.DataTransformWriter;
import org.brunel.build.data.TransformedData;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.guides.AxisBuilder;
import org.brunel.build.guides.ChartTitleBuilder;
import org.brunel.build.guides.LegendBuilder;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.Accessibility;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.SVGGroupUtility;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisElement;
import org.brunel.model.VisException;
import org.brunel.model.VisTypes;
import org.brunel.model.style.StyleSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds a chart consisting of a set of overlaid elements
 */
public class ChartBuilder {

  private final VisInfo visInfo;                      // information on the main visualization
  private final BuilderOptions options;               // options for building
  private final ChartLocation location;                 // where to place the chart
  private final ScriptWriter out;                     // where to write to

  private boolean isMirror = false;

  private ElementBuilder[] elementBuilders;           // Builder for each element
  private ScaleBuilder scalesBuilder;                 // The scales for the current chart
  private AxisBuilder axisBuilder;                    // Axis for the current chart
  private LegendBuilder legendBuilder;                // Legends for the current chart

  public ChartBuilder(VisInfo visInfo, BuilderOptions options, double[] location, ScriptWriter out) {
    this.visInfo = visInfo;
    this.options = options;
    this.location = new ChartLocation(visInfo.width, visInfo.height, location);
    this.out = out;
  }

  public ChartStructure build(int chartIndex, NestingInfo nestingInfo, VisElement... elements) {

    // Assemble the elements and data
    TransformedData[] data = new TransformedData[elements.length];
    for (int i = 0; i < elements.length; i++) {
      data[i] = TransformedData.make(elements[i]);
    }

    // If this is nested, it can only be one element
    boolean nested = nestingInfo.isNested(elements[0]);
    ChartStructure structure = new ChartStructure(chartIndex, elements, location, data, nested, options.visIdentifier);
    structure.accessible = options.accessibleContent;

    defineChart(structure);
    for (ElementStructure e : structure.elementStructure) buildElement(e, nestingInfo);
    endChart(structure);

    return structure;
  }

  private void addElementExports(VisElement vis, DataTransformWriter dataBuilder, ElementStructure structure) {
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

  private void addElementGroups(ElementBuilder builder, ElementStructure structure) {
    String elementTransform = makeElementTransform(structure.chart.coordinates.type);

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

  // Builds controls as needed, then the custom styles, then the visualization
  private void buildElement(ElementStructure structure, NestingInfo nestingInfo) {
    try {

      visInfo.controls.buildControls(structure);                // build controls
      defineElement(structure, nestingInfo);                                // define the element
      // we need to add these to the main style sheet with correct element class identifier
      if (structure.vis.styles != null) {
        StyleSheet styles = structure.vis.styles.replaceClass("currentElement", "element" + structure.elementID());
        visInfo.visStyles.add(styles, "chart" + structure.chart.chartID());
      }
      visInfo.allElements.add(structure);                        // store the built data
    } catch (Exception e) {
      throw VisException.makeBuilding(e, structure.vis);
    }
  }

  private void createBuilders(ChartStructure structure) {
    scalesBuilder = new ScaleBuilder(structure, out);
    legendBuilder = new LegendBuilder(structure, out);
    axisBuilder = new AxisBuilder(structure, scalesBuilder, out);

    ElementStructure[] structures = structure.elementStructure;
    elementBuilders = new ElementBuilder[structures.length];
    for (int i = 0; i < structures.length; i++)
      elementBuilders[i] = ElementBuilder.make(structures[i], out, scalesBuilder);
  }

  private void defineChart(ChartStructure structure) {

    ChartTitleBuilder title = new ChartTitleBuilder(structure, "header");
    ChartTitleBuilder sub = new ChartTitleBuilder(structure, "footer");
    structure.location.setTitleMargins(title.verticalSpace(), sub.verticalSpace());

    // Calculate the margins for this chart within the overall size
    // Create the scales and element builders.   This also creates the interaction instance.
    createBuilders(structure);

    // Write the class definition function
    out.titleComment("Define chart #" + structure.chartID(), "in the visualization");
    out.add("charts[" + structure.chartIndex + "] = function(parentNode, filterRows) {").ln();
    out.indentMore();

    axisBuilder.setAdditionalHAxisOffset(sub.verticalSpace());

    out.add("var geom = BrunelD3.geometry(parentNode || vis.node(),", location.insets, ",", location.innerMargins(), "),")
      .indentMore()
      .onNewLine().add("elements = [];").comment("Array of elements in this chart")
      .indentLess();

    // Transpose if needed
    if (forceSquare(structure.elements)) out.add("geom.makeSquare()").endStatement();
    if (structure.coordinates.isTransposed()) out.add("geom.transpose()").endStatement();

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
      if (axisBuilder.needsAxes()) {
        out.titleComment("Axes");
        axisBuilder.writeAxes();
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

  private void defineElement(ElementStructure structure, NestingInfo info) {

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
    legendBuilder.defineUsageForLegend(structure);

    elementBuilder.preBuildDefinitions();

    // Main method to make a vis
    out.titleComment("Build element from data");

    out.add("function build(transitionMillis) {").ln().indentMore();
    elementBuilder.generate();
    structure.chart.interaction.addHandlers(structure, out);

    // If a chart is nested within us, build its facets
    if (info.nestsOther(structure.vis)) {
      int index = info.indexOfChartNestedWithin(structure.vis);         // Index of nested chart

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

  private void endChart(ChartStructure structure) {
    out.onNewLine().add("function build(time, noData) {").indentMore();

    out.onNewLine().add("var first = elements[0].data() == null").endStatement();
    out.add("if (first) time = 0;").comment("No transition for first call");

    // For coordinate system charts, see if axes are needed
    if (axisBuilder.needsAxes() || structure.geo != null && structure.geo.withGraticule)
      out.onNewLine().add("buildAxes(time)").endStatement();

    Integer[] order = structure.elementBuildOrder();

    out.onNewLine().add("if ((first || time > -1) && !noData) {").indentMore();
    for (int i : order)
      out.onNewLine().add("elements[" + i + "].makeData()").endStatement();
    legendBuilder.writeLegends();
    out.indentLess().onNewLine().add("}").ln();
    if (structure.diagram == VisTypes.Diagram.network)
      out.onNewLine().add("graph = null").endStatement();
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
    if (visInfo.nesting.contains(structure.chartIndex)) {
      // For a nested chart we need to build the chart completely each time, so store the FUNCTION
      out.add("}");
    } else {
      // Non-nested charts just get built once, so execute and store the chart as a built OBJECT
      out.add("}()");
    }

    out.indentLess().endStatement().ln();

  }

  private boolean forceSquare(VisElement[] elements) {
    for (VisElement e : elements) {
      for (Param p : e.fCoords) {
        if (p.asString().equals("square")) return true;
      }
    }
    return false;
  }

  private String makeElementTransform(VisTypes.Coordinates coords) {
    if (coords == VisTypes.Coordinates.transposed)
      return "attr('transform','matrix(0,1,1,0,0,0)')";
    else if (coords == VisTypes.Coordinates.polar)
      return makeTranslateTransform("geom.inner_width/2", "geom.inner_height/2");
    else
      return null;
  }

  private String makeTranslateTransform(String dx, String dy) {
    return "attr('transform','translate(' + " + dx + " + ',' + " + dy + " + ')')";
  }

  private String makeTranslateTransformWithMirroring(String dx, String dy) {
    return "attr('transform','translate(' + " + dx + " + ',' + " + dy + " + ') scale (-1,1)')";
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

    String sTranslate;
    if ("rtl".equals(structure.elements[0].fGuiDir))
      sTranslate = makeTranslateTransformWithMirroring("geom.chart_right", "0");
    else
      sTranslate = makeTranslateTransform("geom.chart_left", "geom.chart_top");
    out.addChained(sTranslate).endStatement();

    structure.interaction.addOverlayForZoom(structure.diagram, out);

    out.add("chart.append('rect').attr('class', 'background')")
      .add(".attr('width', geom.chart_right-geom.chart_left).attr('height', geom.chart_bottom-geom.chart_top)")
      .endStatement();

    String axesTransform;
    //if (isMirror)
    //	axesTransform = makeTranslateTransformWithMirroring("(geom.inner_left + geom.inner_width)", "geom.inner_top");
    //else
    axesTransform = makeTranslateTransform("geom.inner_left", "geom.inner_top");

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

    if (axisBuilder.needsAxes())
      out.add("var axes = chart.append('g').attr('class', 'axis')")
        .addChained(axesTransform).endStatement();
    if (legendBuilder.needsLegends()) {
      sTranslate = makeTranslateTransform("(geom.chart_right-geom.chart_left - 3)", "0");
      out.add("var legends = chart.append('g').attr('class', 'legend')").addChained(sTranslate);
      groupUtil.addAccessibleTitle("Legend");
      out.endStatement();
    }

    if (!structure.nested()) groupUtil.defineInnerClipPath();
  }

}
