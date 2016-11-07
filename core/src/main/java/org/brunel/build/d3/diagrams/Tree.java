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

package org.brunel.build.d3.diagrams;

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.element.D3ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisTypes.Coordinates;

class Tree extends D3Diagram {

    private enum Method {leftRight, topBottom, polar}

    private final Method method;                                    // How to draw it
    private final int labelSize;                                    // Size to leave for labels
    private final int pad = 10;                                     // Pad size
    private final boolean usesSize;                                 // True is size is used

    public Tree(ElementStructure structure, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(structure, data, interaction, out);
        if (vis.coords == Coordinates.polar) method = Method.polar;
        else method = Method.leftRight;
        labelSize = labelBuilder.estimateLabelLength() * 6;
        usesSize = !vis.fSize.isEmpty();
    }

    public ElementDetails initializeDiagram() {
        out.comment("Define tree (hierarchy) data structures");
        makeHierarchicalTree();
        out.add("var treeLayout = d3.tree()");

        if (method == Method.polar) {
            // Pad all around for labels
            out.addChained("size([2*Math.PI, geom.inner_radius-" + (pad + labelSize) + "])")
                    .addChained("separation(function(a,b) { return (a.parent == b.parent ? 1 : 2) / a.depth })");
        } else {
            // Trees default to top-bottom, hence the reversal of coordinates
            out.addChained("size([geom.inner_height-" + 2 * pad + ", geom.inner_width-" + (2 * pad + labelSize) + "])");
        }

        out.endStatement();

        out.add("var treeNodes = treeLayout(tree).descendants()").endStatement();

        if (method != Method.polar)
            out.add("treeNodes.forEach( function(d) { d.x += " + pad + "; d.y += " + pad + "} )").endStatement();

        if (usesSize) {
            // Redefine size to use the node value
            out.add("size = function(d) { return scale_size(d.value) }").endStatement();
        }

        ElementRepresentation rep = method == Method.leftRight ?
                ElementRepresentation.pointLikeCircle : ElementRepresentation.largeCircle;
        return ElementDetails.makeForDiagram(vis, rep, "point", "treeNodes");
    }

    public void writeDiagramEnter() {
        out.add("added.filter(function(d) { return d.parent })");       // Only if it has a parent
        writeNodePlacement("d.parent");                                 // place it at parent position
        out.endStatement();
    }

    public void writeDefinition(ElementDetails details) {
        writeHierarchicalClass();

        writeNodePlacement("d");

        out.addChained("attr('r', " + details.overallSize.halved() + ")");
        addAestheticsAndTooltips(details);

        // We add the tree edges
        // TODO: if there is an edge definition in the chart, use that instead
        out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
        out.add("diagramExtras.attr('class', 'diagram tree edge')").endStatement();

        out.add("var edgeGroup = diagramExtras.selectAll('path').data(tree.links(), edgeKey)")
                .endStatement();
        out.add("var added = edgeGroup.enter().append('path').attr('class', 'edge')");
        writeEdgePlacement(true);

        out.add("BrunelD3.transition(edgeGroup.merge(added), transitionMillis)");
        writeEdgePlacement(false);

        D3ElementBuilder.writeRemovalOnExit(out, "edgeGroup");

        labelBuilder.addTreeInternalLabelsOutsideNode(
                method == Method.leftRight || !usesSize ? "bottom" : "center"
        );

    }


    private void writeEdgePlacement(boolean grow) {
        String target = grow ? "source" : "target";

        out.addChained("attr('d', function(d) {")
                .indentMore().indentMore().onNewLine();

        if (method == Method.polar) {
            out.add("var r1 = d.source.y, a1 = d.source.x, r2 = d." + target + ".y, a2 = d." + target + ".x, r = (r1+r2)/2").endStatement()
                    .add("return 'M' + scale_x(r1*Math.cos(a1)) + ',' + scale_y(r1*Math.sin(a1)) ")
                    .continueOnNextLine().add(" + 'Q' +  scale_x(r*Math.cos(a2)) + ',' + scale_y(r*Math.sin(a2))")
                    .continueOnNextLine().add(" + ' ' +  scale_x(r2*Math.cos(a2)) + ',' + scale_y(r2*Math.sin(a2))")
                    .endStatement();
        } else {
            out.add("var x1 =  scale_x(d.source.y), y1 = scale_y(d.source.x), x2 = scale_x(d." + target + ".y), y2 = scale_y(d." + target + ".x)").endStatement()
                    .add("return 'M' + x1 + ',' + y1 ")
                    .continueOnNextLine().add(" + 'C' + (x1+x2)/2 + ',' + y1")
                    .continueOnNextLine().add(" + ' ' + (x1+x2)/2 + ',' + y2")
                    .continueOnNextLine().add(" + ' ' + x2 + ',' + y2")
                    .endStatement();
        }
        out.indentLess().indentLess().add("})").endStatement();

    }

    private void writeNodePlacement(String d) {
        if (method == Method.leftRight) {
            out.addChained("attr('cx', function(d) { return scale_x(" + d + ".y) })")
                    .addChained("attr('cy', function(d) { return scale_y(" + d + ".x) })");
        } else if (method == Method.topBottom) {
            out.addChained("attr('cx', function(d) { return scale_x(" + d + ".x) })")
                    .addChained("attr('cy', function(d) { return scale_y(" + d + ".y) })");
        } else if (method == Method.polar) {
            out.addChained("attr('cx', function(d) { return scale_x(" + d + ".y * Math.cos(" + d + ".x)) })")
                    .addChained("attr('cy', function(d) { return scale_y(" + d + ".y * Math.sin(" + d + ".x)) })");
        }
    }

    public boolean needsDiagramExtras() {
        return true;
    }

    public boolean needsDiagramLabels() {
        return true;
    }

}
