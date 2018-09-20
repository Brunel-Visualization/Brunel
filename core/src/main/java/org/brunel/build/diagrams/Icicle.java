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

package org.brunel.build.diagrams;

import org.brunel.build.LabelBuilder;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.style.StyleTarget;

class Icicle extends D3Diagram {

  private final Padding padding;            // Amount to pad the icicle / sunburst arcs
  private final boolean sunburst;            // If true, we are a polar sunburst, otherwise an icicle plot

  public Icicle(ElementStructure structure) {
    super(structure);
    padding = ModelUtil.getPadding(vis, StyleTarget.makeElementTarget(null, "element"), 0);
    this.sunburst = structure.chart.getCoordinates(structure.index).isPolar();
  }

  public void writeDataStructures(ScriptWriter out) {
    out.comment("Define icicle (hierarchy) data structures");
    makeHierarchicalTree(true, out);

    // Create the d3 layout
    out.add("d3.partition()")
      .addChained(sunburst ? "size([2*Math.PI, 1])" : "size([geom.inner_width, 1])");
    if (padding.horizontal() != 0) {
      double v = sunburst ? padding.horizontal() / 100.0 : padding.horizontal();
      out.addChained("padding(" + v + ")");
    }
    out.add("(tree)").endStatement();

    out.add("var maxDepth = 0; tree.descendants().forEach(function(i) {maxDepth = Math.max(maxDepth, i.depth)})")
      .endStatement();

  }

  public void writePerChartDefinitions(ScriptWriter out) {
    super.writePerChartDefinitions(out);
    out.add("var graph;").comment("The tree with links");
  }

  public ElementDetails makeDetails() {
    if (sunburst)
      return new ElementDetails(structure.vis, ElementRepresentation.generalPath, "wedge", "tree.descendants()", true);
    else
      return ElementDetails.makeForDiagram(structure, ElementRepresentation.rect, "box", "tree.descendants()");

  }

  public void defineCoordinateFunctions(ElementDetails details, ScriptWriter out) {

    if (sunburst) {
      // Define the arcs used for the wedge

      out.add("function depth_scale(x) { return geom.inner_radius * scale_y((x-0.5) / (maxDepth+0.5)) }").endStatement();

      out.add("var path = d3.arc()")
        .addChained("startAngle(function(d) { return scale_x(d.x0); })")
        .addChained("endAngle(function(d) { return scale_x(d.x1); })")
        .addChained("innerRadius(function(d) { return depth_scale(d.depth)" + padding.topModifier() + "; })")
        .addChained("outerRadius(function(d) { return depth_scale(d.depth+1)" + padding.bottomModifier() + "; })")
        .endStatement();
    } else {
      out.add("function h(x) { return geom.inner_height * scale_y( (x-1) / maxDepth )" + padding.topModifier() + "}")
        .endStatement()
        .add("var w = geom.inner_height / maxDepth " + padding.heightModifier())
        .endStatement();
    }
  }

  public boolean needsDiagramLabels() {
    return true;
  }

  public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
    writeHierarchicalClass(out);
    if (sunburst) {
      out.addChained("attr('d', path)");
    } else {
      out.addChained("attr('x', function(d) { return scale_x(d.x0) })")
        .addChained("attr('y', function(d) { return h(d.depth) })")
        .addChained("attr('width', function(d) { return scale_x(d.x1) - scale_x(d.x0) })")
        .addChained("attr('height', w)");
    }
    ElementBuilder.writeElementAesthetics(details, true, vis, out);
  }

  public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
    ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
  }

}
