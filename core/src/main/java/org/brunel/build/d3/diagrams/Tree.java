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

import org.brunel.build.d3.D3Util;
import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Coordinates;

class Tree extends D3Diagram {

    private final int pad;          // Amount to pad tree by

    public Tree(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
        this.pad = 10;
    }

    public ElementDetails initializeDiagram() {
        out.comment("Define tree (hierarchy) data structures");
        makeHierarchicalTree();
        out.add("var treeLayout = d3.layout.tree()")
                .addChained("sort(BrunelData.diagram_Hierarchical.compare)");
        if (vis.coords == Coordinates.polar) {
            out.addChained("size([360, geom.inner_radius-" + pad + "])");
        } else {
            out.addChained("size([geom.inner_width-" + 2 * pad + ", geom.inner_height-" + 2 * pad + "])");
        }
        out.endStatement();
        out.add("function keyFunction(d) { return d.key }").endStatement();

        // Do not override the polar coordinates!
        if (vis.coords != Coordinates.polar)
            out.add("elementGroup.attr('transform', 'translate(" + pad + ", " + pad + ")')").endStatement();
        return ElementDetails.makeForDiagram(vis, ElementRepresentation.largeCircle, "treeLayout(tree.root)", "point");
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('class', function(d) { return (d.children ? 'L' + d.depth : 'leaf element " + element.name() + "') })");

        if (vis.coords == Coordinates.polar) {
            out.addChained("attr('transform', function(d) { return 'rotate(' + (d.x - 90) + ') translate(' + d.y + ')' })");
        } else {
            out.addChained("attr('cx', function(d) { return d.x })")
                    .addChained("attr('cy', function(d) { return d.y })");
        }
        out.addChained("attr('r', " + D3Util.defineSafeRadius(elementDef.overallSize) + ")").endStatement();

//        addLabels(details, elementDef);

        addAestheticsAndTooltips(details, true);

        // We add the tree edges
        // TODO: if there is an edge definition in the chart, use that instead
        out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
        out.add("diagramExtras.attr('class', 'diagram tree edge')").endStatement();

        // The edges
        out.add("var edgeGroup = diagramExtras.selectAll('path').data(treeLayout.links(d3Data))").endStatement();
        out.add("edgeGroup.enter().append('path').attr('class', 'edge')").endStatement();
        out.add("BrunelD3.trans(edgeGroup,transitionMillis)")
                .addChained("attr('d', d3.svg.diagonal");

        if (vis.coords == Coordinates.polar) {
            out.add(".radial().projection(function(d) { return [d.y, d.x / 180 * Math.PI] }))");
        } else {
            out.add("())");
        }

        out.endStatement();

        addAestheticsAndTooltips(details, true);
    }

//    private void addLabels(ElementDetails details, ElementDefinition elementDef) {
//
//        out.add("diagramLabels.attr('class', 'axis diagram tree hierarchy')").endStatement();
//        out.add("var treeLabels = diagramLabels.selectAll('text').data(d3Data)").endStatement();
//
//        out.add("treeLabels.enter().append('text')")
//                .addChained("attr('class', function(d) { return 'axis label L' + d.depth })")
//                .addChained("style('text-anchor', 'middle')")
//                .addChained("attr('dy', '0.85em')").endStatement();
//
//        out.add("var treeLabeling = {method:'bottom', fit:false, content:function(d){return d.innerNodeName} }").endStatement();
//        out.add("BrunelD3.tween(treeLabels,transitionMillis, function(d, i) { return BrunelD3.makeLabeling(this, selection[0][i], treeLabeling, false)})");
//        out.endStatement();
//
//        out.add("treeLabels.exit().remove()").endStatement();
//
//    }

    public boolean needsDiagramExtras() {
        return true;
    }

    public boolean needsDiagramLabels() {
        return true;
    }
}
