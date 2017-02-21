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

import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.element.ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.model.style.StyleTarget;

class Chord extends D3Diagram {

    public Chord(ElementStructure vis) {
        super(vis);
    }

    public String getRowKeyFunction() {
        return "function(d) { return d.source.index + '|' + d.target.index }";
    }

    public void writeDataStructures(ScriptWriter out) {
        String f1 = vis.positionFields()[0];
        String f2 = position[1];

        out.comment("Define chord data structures using Brunel's enhanced chord diagram builder");
        out.add("var chords = BrunelData.diagram_Chord.make(processed,");

        // Always need from and to, but the size may be empty when building the chord data
        if (size == null) out.addQuoted(f1, f2);
        else out.addQuoted(f1, f2, size);
        out.add(")").endStatement();

        // take arc path font size into account, adding a bit of padding, to define the arc width
        StyleTarget target = StyleTarget.makeElementTarget("text", "axis", "label");
        double labelSize = ModelUtil.getSize(vis, target, "font-size", 8);
        double arcWidth = labelSize * 1.2;
        out.add("var arc_width =", Data.formatNumeric(arcWidth, null, false), ";").comment("Width of exterior arc");
        out.add("function keyFunction(d) { return d.source.index + '|' + d.target.index };").comment(" special key function for the edges");

        // Scaled size and offsets
        out.add("var R = scale_x(geom.inner_radius)-scale_x(0), svgTrans = 'translate(' + scale_x(0) + ',' + scale_y(0) + ')'").endStatement();
    }

    public ElementDetails makeDetails() {
        return ElementDetails.makeForDiagram(structure, ElementRepresentation.polygon, "edge", "chords.chords");
    }

    public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {


        // The Chords themselves are simple to create
        out.addChained("attr('d', d3.ribbon().radius(R-arc_width))")
                .addChained("attr('class', 'element " + element.name() + "')")
                .addChained("attr('transform', svgTrans)");

		ElementBuilder.writeElementAesthetics(details, true, vis, out);

        // We now need to add the arcs on the outside for the groups
        out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
        out.add("diagramExtras.attr('class', 'diagram chord arcs')").endStatement();

        // Each chord group will be a group with a donut arc and a text path in it
        out.add("var arcGroup = diagramExtras.selectAll('g').data(chords.groups),")
                .continueOnNextLine().add("addedArcGroups = arcGroup.enter().append('g'),")
                .continueOnNextLine().add("arcPath = d3.arc().innerRadius(R - arc_width).outerRadius(R)")
                .endStatement();

        // Add the two parts to each group, linking them through an ID
        out.add("addedArcGroups.append('path').attr('class', 'box')")
                .addChained("attr('id', function(d, i) { return 'arc' + i; })")
                .endStatement();

        out.add("addedArcGroups.append('text').attr('class', 'label')")
                .addChained("attr('dy', arc_width*0.72).attr('class', 'label')")
                .addChained("append('textPath').attr('xlink:href', function(d, i) { return '#arc' + i })")
                .endStatement();

        out.add("var mergedArcGroups = addedArcGroups.merge(arcGroup)").endStatement();

        // Transition to correct arc sizes and locations
        out.add("BrunelD3.tween(mergedArcGroups, transitionMillis, function(d, i) { ")
                .indentMore().indentMore().onNewLine()
                .add("var group = d3.select(this)").endStatement()
                .add("group.attr('transform', svgTrans)").endStatement()
                .add("return function() {").indentMore().indentMore().onNewLine()
                .add("group.select('path').attr('d', arcPath(d))").endStatement()
                .add("group.select('textPath').text(d.name)").endStatement()
                .add("BrunelD3.centerInWedge(group.select('text'), arc_width)").endStatement()
                .indentLess().indentLess().onNewLine().add("}")
                .indentLess().indentLess().onNewLine().add("})").endStatement();

        // Ensure removal on exit
		ElementBuilder.writeRemovalOnExit(out, "arcGroup");
    }

    public boolean needsDiagramExtras() {
        return true;
    }

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}
}
