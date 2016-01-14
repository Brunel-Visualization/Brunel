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

import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Treemap extends D3Diagram {

    public Treemap(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
    }

    public ElementDetails initalizeDiagram() {
        out.comment("Define treemap (hierarchy) data structures");
        makeHierarchicalTree();

        // Create the d3 layout
        out.add("var treemap = d3.layout.treemap().sticky(true)")
                .addChained("size([geom.inner_width, geom.inner_height])")
                .addChained("sort(BrunelData.diagram_Hierarchical.compare)")
                .addChained("value(function(d) { return d.value == null || d.value < 0 ? 0 : d.value })")
                .addChained("padding(function(d) { if (d.depth < 2) return [14,2,2,2]; if (d.depth < 3) return [11,2,2,2];})").endStatement();
        out.add("function keyFunction(d) { return d.key }").endStatement();
        return ElementDetails.makeForDiagram(vis, "treemap(tree.root)", "rect", "polygon", "box", true);
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('class', function(d) { return (d.children ? 'L' + d.depth : 'leaf element " + element.name() + "') })")
                .addChained("attr('x', function(d) { return d.x; })")
                .addChained("attr('y', function(d) { return d.y; })")
                .addChained("attr('width', function(d) { return d.dx; })")
                .addChained("style('width', function(d) { return d.dx; })")
                .addChained("attr('height', function(d) { return d.dy; })").endStatement();

        labelBuilder.addTreeInternalLabels();
        addAestheticsAndTooltips(details, true);
    }

    public boolean needsDiagramLabels() {
        return true;
    }
}
