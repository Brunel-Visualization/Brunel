/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.build.d3.diagrams;

import org.brunel.build.d3.ElementDefinition;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Bubble extends D3Diagram {

    public Bubble(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
    }

    public ElementDetails writeDataConstruction() {
        out.comment("Define bubble (hierarchy) data structures");

        makeHierarchicalTree();
        out.add("var pack = d3.layout.pack().size([geom.inner_width, geom.inner_height])")
                .addChained("value(function(d) { return d.value == null || d.value < 0 ? 0 : d.value })")
                .addChained("sort(BrunelData.diagram_Hierarchical.compare)").endStatement();
        out.add("function keyFunction(d) { return d.key }").endStatement();
        return ElementDetails.makeForDiagram("pack(tree.root)", "circle", "point", "box", true);
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        // Simple circles, with classes defined for CSS
        out.addChained("attr('class', function(d) { return (d.children ? 'L' + d.depth : 'leaf element " + element.name() + "') })")
                .addChained("attr('cx', function(d) { return d.x; })")
                .addChained("attr('cy', function(d) { return d.y; })")
                .addChained("attr('r', function(d) { return d.r; })").endStatement();
        addAestheticsAndTooltips(details, true);
    }
}
