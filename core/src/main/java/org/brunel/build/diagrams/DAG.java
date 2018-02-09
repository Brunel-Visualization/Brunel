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

import org.brunel.action.Param;
import org.brunel.build.ScaleBuilder;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.GeomAttribute;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.style.StyleTarget;

import java.util.List;

class DAG extends Network {

  private int pad;                                                // Pad size

  public DAG(ElementStructure structure) {
    super(structure);
    double sizeFactor = getSize(vis.fSize);

    StyleTarget target = StyleTarget.makeElementTarget("point", "element");
    ModelUtil.Size size = ModelUtil.getSize(vis, target, "size");
    pad = (int) Math.ceil(size == null ? structure.chart.defaultPointSize() : size.value(10) / 2);
    if (!vis.fSize.isEmpty()) {
      pad = (int) Math.ceil(pad * sizeFactor);
    }
    pad += 3;      // A little extra for borders etc.
  }

  private double getSize(List<Param> fSize) {
    // Guesses the maximum size a node might be for the size aesthetic
    if (fSize.isEmpty()) {
      return 1;              // No scaling
    }
    if (!fSize.get(0).hasModifiers()) {
      return 1;        // range is [0,1] so max is one
    }

    // we have a defined maximum size, so return it
    Double[] sizes = ScaleBuilder.getSizes(fSize.get(0).modifiers()[0].asList());
    return sizes[sizes.length - 1];
  }

  public void defineCoordinateFunctions(ElementDetails details, ScriptWriter out) {
    GeomAttribute rr = details.overallSize.halved();
    defineXYR("scale_x(d.x)", "scale_y(d.y)", "d.radius = " + rr.definition(), details, out);
  }

  public void writeDataStructures(ScriptWriter out) {
    super.writeDataStructures(out);
    out.add("new BrunelData.diagram_DAG(graph)")
      .addChained("size(geom.inner_width, geom.inner_height).pad(" + pad + ")")
      .addChained("layout()").endStatement();
  }

  public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
    ElementBuilder.definePointLikeMark(details, structure, out, false);
    ElementBuilder.writeElementAesthetics(details, true, vis, out);
  }

  public void writePerChartDefinitions(ScriptWriter out) {
    out.add("var graph;").comment("The graph to display");
  }

  public void writeBuildCommands(ScriptWriter out) {
    // Not needed
  }
}
