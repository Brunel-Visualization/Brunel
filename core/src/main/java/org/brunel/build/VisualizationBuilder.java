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
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisComposition;
import org.brunel.model.VisElement;
import org.brunel.model.VisItem;
import org.brunel.model.VisTypes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
   *
   * @return builder
   */
  public static VisualizationBuilder make() {
    return make(new BuilderOptions());
  }

  /**
   * Return the required builder with requested options
   *
   * @param options options to use
   * @return builder
   */
  public static VisualizationBuilder make(BuilderOptions options) {
    return new VisualizationBuilder(options);
  }

  private final BuilderOptions options;        // How to build
  private VisInfo visStructure;                // Information on the main structure
  private NestingInfo nestingInfo;             // How elements are nested within element
  private ScriptWriter out;                    // Where to write code

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
    this.nestingInfo = new NestingInfo();

    // Define defaults and ensure everything is good to go
    main = main.makeCanonical();

    // Index the datasets with the number in the list of input data sets
    Dataset[] datasets = main.getDataSets();
    for (int i = 0; i < datasets.length; i++) datasets[i].set("index", i);

    // Create the main visualization area
    writeStart();

    Map<VisItem, double[]> locations = new LinkedHashMap<>();     // Where to place items
    VisItem[] parts = main.children();                            // The parts contained in this item
    if (parts != null && ((VisComposition) main).method == VisTypes.Composition.tile) {
      // We have tiled locations, so read them all in
      ChartLayout layout = new ChartLayout(width, height, parts);   // Layout method
      for (int i = 0; i < parts.length; i++) {
        VisItem chart = parts[i];
        double[] location = layout.getLocation(i);
        locations.put(chart, location);
      }
    } else {
      // we have a single top level item (might be composition)
      locations.put(main, new ChartLayout(width, height, main).getLocation(0));
    }

    // Write all the regular charts
    int chartIndex = 0;
    for (Map.Entry<VisItem, double[]> e : locations.entrySet())
      buildChart(e.getKey(), e.getValue(), chartIndex++);

    // Write any nested charts
    for (NestingInfo.NestedItem item : nestingInfo.items) {
      visStructure.nesting.add(chartIndex);                                           // This chart is a nested one
      double[] loc = new ChartLayout(width, height, item.inner).getLocation(0);
      new ChartBuilder(visStructure, options, loc, out).build(chartIndex, nestingInfo, item.inner);
      chartIndex++;
    }

    writeEnd(main);
  }

  private VisItem makeCanonical(VisItem item) {
    VisItem[] children = item.children();
    if (children == null)
      return item.getSingle().makeCanonical();
    else for (int i = 0; i < children.length; i++)
      children[i] = makeCanonical(children[i]);
    return item;
  }

  private void buildChart(VisItem item, double[] location, int chartIndex) {
    VisElement[] elements;                          // Elements to build for this chart

    VisItem[] children = item.children();
    VisTypes.Composition compositionMethod = children == null ? null : ((VisComposition) item).method;

    if (compositionMethod == VisTypes.Composition.overlay) {
      elements = new VisElement[children.length];
      for (int i = 0; i < children.length; i++) elements[i] = toMainElement(children[i]);
    } else {
      // Main item is either simple or a nesting
      elements = new VisElement[]{toMainElement(item)};
    }

    new ChartBuilder(visStructure, options, location, out).build(chartIndex, nestingInfo, elements);
  }

  private VisElement toMainElement(VisItem item) {
    VisItem[] children = item.children();
    // Simple case -- the item is an element
    if (children == null) return item.getSingle();

    // Must be a nesting
    if (children.length != 2) throw new IllegalStateException("Nesting requires two children");
    VisElement outer = (VisElement) children[0];
    VisElement inner = (VisElement) children[1];
    nestingInfo.add(inner, outer);
    return outer;
  }

  public String getLanguage() {
    return visStructure.getLanguage();
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

    out.add("function updateAll(time) { charts.forEach(function(x) { if (x.build) x.build(time || 0)}) }").ln();

    // Define visualization functions
    out.add("function buildAll() {").ln().indentMore()
      .add("for (var i=0;i<arguments.length;i++) setData(arguments[i], i)").endStatement()
      .add("updateAll(transitionTime)").endStatement();
    out.indentLess().add("}").ln().ln();

    // Define nesting info
    if (nestingInfo.facetsExist()) {
      out.onNewLine().comment("Define facet nesting relationships");
      for (NestingInfo.NestedItem item : nestingInfo.items) {
        ElementStructure outerElement = visStructure.findElement(item.outer);
        ElementStructure innerElement = visStructure.findElement(item.inner);
        //      String chartID = "g.chart" + ChartStructure.makeChartID(index);
        out.add("charts[" + outerElement.chart.chartIndex + "].elements[" + outerElement.index + "]")
          .add(".facet = { chartID:'g.chart" + innerElement.chart.chartID() + "', index:" + innerElement.chart.chartIndex + "}")
          .endStatement();
      }
    }

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
    if (!(main instanceof VisElement))
      return;
    VisElement element = (VisElement) main;

    if (element.fLocale == null
      && element.fTextDir == null
      && element.fGuiDir == null
      && element.fNumShape == null)
      return;

    out.add("var BrunelD3Locale;\n");
    out.add("bidiProcessing('"
      + options.visIdentifier + "', '"
      + element.fLocale + "', '" + element.fTextDir + "', '"
      + element.fGuiDir + "', '" + element.fNumShape + "');");

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
