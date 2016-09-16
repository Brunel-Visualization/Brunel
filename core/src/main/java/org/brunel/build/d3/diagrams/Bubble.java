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

class Bubble extends D3Diagram {

    public Bubble(VisSingle vis, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(vis, data, interaction, out);
    }

    public ElementDetails initializeDiagram() {
        out.comment("Define bubble (hierarchy) data structures");

        makeHierarchicalTree();

        out.add("var pack = d3.pack().size([geom.inner_width, geom.inner_height])").endStatement();
        return ElementDetails.makeForDiagram(vis, ElementRepresentation.spaceFillingCircle, "point", "pack(tree).descendants()");
    }

    public void writeDiagramEnter() {
        out.add("added.filter(function(d) { return d.parent })")       // Only if it has a parent
                .addChained("attr('cx', function(d) { return scale_x(d.parent.x) })")
                .addChained("attr('cy', function(d) { return scale_y(d.parent.y) })")
                .addChained("attr('r', 0)");
        out.endStatement();
    }

    public void writeDefinition(ElementDetails details) {
        writeHierarchicalClass();
        out.addChained("filter(function(d) { return d.depth })");
        out.addChained("attr('cx', function(d) { return scale_x(d.x) })")
                .addChained("attr('cy', function(d) { return scale_y(d.y) })")
                .addChained("attr('r', function(d) { return scale_x(d.r) - scale_x(0) })");
        addAestheticsAndTooltips(details);
    }

}
