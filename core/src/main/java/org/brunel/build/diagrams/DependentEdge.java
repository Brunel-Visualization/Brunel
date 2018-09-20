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
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisTypes;

class DependentEdge extends D3Diagram {

  public static void write(boolean curved, ElementStructure structure, ScriptWriter out, String groupName) {
    // Create paths for the added items, and grow the from the source
    out.add("var added = " + "edgeGroup" + ".enter().append('path').attr('class', 'edge')");
    writeEdgePlacement("source", curved, structure, out);
    out.endStatement();

    // Create paths for all items, and transition them to the final locations
    out.add("BrunelD3.transition(" + groupName + ".merge(added), transitionMillis)");
    writeEdgePlacement("target", curved, structure, out);
    out.endStatement();
  }

  private static void writeEdgePlacement(String target, boolean curved, ElementStructure structure, ScriptWriter out) {

    out.addChained("attr('d', function(d) {")
      .indentMore().indentMore().onNewLine();

    if (!structure.chart.diagramDefinesGraph()) {
      // The node locations have already been fully transformed, so just use them
      out.add("var p = BrunelD3.insetEdge(d.source.x, d.source.y, d.source, d.target.x, d.target.y, d.target)")
        .endStatement();
      defineCurve(curved, out);
    } else if (structure.chart.getCoordinates(structure.index).isPolar()) {
      out.add("var r1 = d.source.y, a1 = d.source.x, r2 = d." + target + ".y, a2 = d." + target + ".x, r = (r1+r2)/2").endStatement()
        .add("return 'M' + scale_x(r1*Math.cos(a1)) + ',' + scale_y(r1*Math.sin(a1)) +");

      // Add curve if requested, else just a straight line
      if (curved) {
        out.ln().indent().add("'Q' +  scale_x(r*Math.cos(a2)) + ',' + scale_y(r*Math.sin(a2)) + ' '");
      } else {
        out.ln().indent().add("'L'");
      }

      out.ln().indent().add(" +  scale_x(r2*Math.cos(a2)) + ',' + scale_y(r2*Math.sin(a2))")
        .endStatement();
    } else {
      if (structure.chart.diagram == VisTypes.Diagram.dag) {
        // This does not need coordinates flipped
        out.add("var p = BrunelD3.insetEdge(scale_x(d.source.x), scale_y(d.source.y), d.source,").ln().indent().add("scale_x(d.target.x), scale_y(d.target.y), d.target)")
          .endStatement();
      } else {
        out.add("var p = BrunelD3.insetEdge(scale_x(d.source.y), scale_y(d.source.x), d.source,").ln().indent().add("scale_x(d.target.y), scale_y(d.target.x), d.target)")
          .endStatement();
      }
      defineCurve(curved, out);
    }
    out.indentLess().indentLess().add("})");

  }

  private static void defineCurve(boolean curved, ScriptWriter out) {
    out.add("if(!p) return ''").endStatement();

    out.add("return 'M' + p.x1 + ',' + p.y1 + ");
    // Add curve if requested, else just a straight line
    if (curved) {
      out.ln().indent().add("'C' + (p.x1+p.x2)/2 + ',' + p.y1").ln().indent().add(" + ' ' + (p.x1+p.x2)/2 + ',' + p.y2 + ' ' ");
    } else {
      out.ln().indent().add("'L'");
    }

    out.ln().indent().add("+ p.x2 + ',' + p.y2").endStatement();
  }

  public final boolean curved;        // True if we want a curved arc
  private final boolean arrow;        // True if we want arrows
  private final boolean isTree;        // True for hierarchical structures

  DependentEdge(ElementStructure structure) {
    super(structure);
    VisTypes.Diagram diagram = structure.chart.diagram;
    this.isTree = diagram == VisTypes.Diagram.tree;
    String symbol = structure.styleSymbol;
    if (symbol == null) {
      // The default is to show arrows, but only curved lines for hierarchies
      this.arrow = true;
      this.curved = diagram == VisTypes.Diagram.tree;
    } else {
      this.arrow = symbol.toLowerCase().contains("arrow");
      this.curved = symbol.toLowerCase().contains("curved") || symbol.toLowerCase().contains("arc");
    }
  }

  public String getRowKeyFunction() {
    return "function(d) { return d.key }";
  }

  public String getStyleClasses() {
    return "'diagram hierarchy edge'";
  }

  public ElementDetails makeDetails() {
    String dataDef;
    if (isTree) {
      dataDef = "validEdges(tree, graph.links)";
    } else if (!structure.chart.diagramDefinesGraph()) {
      dataDef = "edgeGraph.links";
    } else {
      dataDef = "graph.links";
    }
    return ElementDetails.makeForDiagram(structure, ElementRepresentation.curvedPath, "edge", dataDef);
  }

  public void writeDataStructures(ScriptWriter out) {
    if (!structure.chart.diagramDefinesGraph()) {

      // The diagram does not build nodes, so we must do so by manually building edges that link two nodes
      out.add("edgeGraph.links = data._rows.map(function(d) { return BrunelD3.makeEdge(d, edgeGraph.nodes) })")
        .addChained("filter(function(x) { return x != null})")
        .comment("Add links to the nodes for each row");
    }
  }

  public void writeDiagramEnter(ElementDetails details, LabelBuilder labelBuilder, ScriptWriter out) {
    if (arrow) {
      out.addChained("attr('marker-end', 'url(#arrow)')");
    }
  }

  public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
    writeEdgePlacement("target", curved, structure, out);
    ElementBuilder.writeElementAesthetics(details, true, vis, out);
  }

  public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
    ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
  }

  public void preBuildDefinitions(ScriptWriter out) {
    // Ensure that we have a valid list of identifiers (from a pruned tree)
    if (isTree) {
      out.add("function validEdges(edges) {").comment("Strip out pruned edges").indentMore()
        .add("var V={};").comment("Stores valid node IDs")
        .add("tree.descendants().forEach(function(x) { V[x.data.key] = 1})").endStatement()
        .add("return graph.links.filter(function(x) {return V[x.source.key] && V[x.target.key]})")
        .endStatement().indentLess()
        .add("}");
    }
  }

  public void writePerChartDefinitions(ScriptWriter out) {
    // If a graph has not been defined by a diagram, we must make one
    // This defines the structure; the node element will define nodes and this element will build links
    if (!structure.chart.diagramDefinesGraph()) {
      out.add("var edgeGraph = { nodes:{}, edges:[] };")
        .comment("Filled in by element build calls");
    }

    // ensure an arrowhead is defined
    if (arrow) {
      out.add("vis.append('svg:defs').selectAll('marker').data(['arrow']).enter()")
        .addChained("append('svg:marker').attr('id', 'arrow')")
        .addChained("attr('viewBox', '0 -6 10 10').attr('orient', 'auto')")
        .addChained("attr('refX', 7).attr('refY', 0)")
        .addChained("attr('markerWidth', 6).attr('markerHeight', 6)")
        .addChained("attr('fill', '#888888')")
        .addChained("append('svg:path').attr('d', 'M0,-4L8,0L0,4')")
        .endStatement();
    }

  }

}
