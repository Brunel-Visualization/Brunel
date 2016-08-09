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
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Coordinates;

class Tree extends D3Diagram {

    private enum Method {leftRight, topBottom, polar}

    private final Method method;                                    // How to draw it
    private final int labelSize;                                    // Size to leave for labels
    private final int pad = 10;                                     // Pad size
    private final boolean usesSize;                                 // True is size is used

    public Tree(VisSingle vis, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(vis, data, interaction, out);
        if (vis.coords == Coordinates.polar) method = Method.polar;
        else if (vis.coords == Coordinates.transposed) method = Method.topBottom;
        else method = Method.leftRight;

        labelSize = labelBuilder.estimateLabelLength() * 6;

        usesSize = !vis.fSize.isEmpty();
    }

    public ElementDetails initializeDiagram() {
        out.comment("Define tree (hierarchy) data structures");
        makeHierarchicalTree();
        out.add("var treeLayout = d3.tree()");

        if (method == Method.leftRight) {
            // Trees default to top-bottom, hence the reversal of coordinates
            out.addChained("size([geom.inner_height-" + 2 * pad + ", geom.inner_width-" + (2 * pad + labelSize) + "])");
        }

        out.endStatement();

        out.add("var treeNodes = treeLayout(tree).descendants()").endStatement();
        out.add("treeNodes.forEach( function(d) { d.x += " + pad + "; d.y += " + pad + "} )").endStatement();
        out.add("function keyFunction(d) { return d.key }").endStatement();

        //        if (vis.coords == Coordinates.polar) {
//            out.addChained("size([360, geom.inner_radius-" + pad + "])")
//                    .addChained("separation(function(a,b) { return (a.parent == b.parent ? 1 : 2) / a.depth }");
//        } else {


        if (usesSize) {
            // Redefine size to use the node value
            out.add("size = function(d) { return scale_size(d.value) }").endStatement();
        }

        return ElementDetails.makeForDiagram(vis, ElementRepresentation.pointLikeCircle, "point", "treeNodes");
    }

    public void writeDefinition(ElementDetails details) {
        out.addChained("attr('class', function(d) { return (d.children ? 'element L' + d.depth : 'leaf element " + element.name() + "') })");

//        if (vis.coords == Coordinates.polar) {
//            out.addChained("attr('transform', function(d) { return 'rotate(' + (d.x - 90) + ') translate(' + d.y + ')' })");
//        } else {
//        }

        if (method == Method.leftRight) {
            out.addChained("attr('cx', function(d) { return d.y })")
                    .addChained("attr('cy', function(d) { return d.x })");
        }

        out.addChained("attr('r', " + details.overallSize.halved() + ")");
        addAestheticsAndTooltips(details);

        // We add the tree edges
        // TODO: if there is an edge definition in the chart, use that instead
        out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
        out.add("diagramExtras.attr('class', 'diagram tree edge')").endStatement();

        out.add("var edgeGroup = diagramExtras.selectAll('path').data(tree.links())").endStatement();
        out.add("var added = edgeGroup.enter().append('path').attr('class', 'edge')").endStatement();
        out.add("BrunelD3.transition(edgeGroup.merge(added), transitionMillis)")
                .addChained("attr('d', function(d) {")
                .indentMore().indentMore().onNewLine()
                .add("return 'M' + d.source.y + ',' + d.source.x ")
                .continueOnNextLine().add(" + 'C' + (d.source.y + d.target.y)/2 + ',' + d.source.x")
                .continueOnNextLine().add(" + ' ' + (d.source.y + d.target.y)/2 + ',' + d.target.x")
                .continueOnNextLine().add(" + ' ' + d.target.y + ',' + d.target.x")
                .endStatement()
                .indentLess().indentLess().add("})").endStatement();


        labelBuilder.addTreeInternalLabelsBelowNode();


//
//        if (vis.coords == Coordinates.polar) {
//            out.add(".radial().projection(function(d) { return [d.y, d.x / 180 * Math.PI] }))");
//        } else {
//            out.add("())");
//        }
    }

    public boolean needsDiagramExtras() {
        return true;
    }

    public boolean needsDiagramLabels() {
        return true;
    }
}
