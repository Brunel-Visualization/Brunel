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
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ModelUtil.Size;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Chord extends D3Diagram {

    public Chord(VisSingle vis, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(vis, data, interaction, out);
    }

    public String getRowKey() {
        return "d.source.index + '|' + d.target.index";
    }

    public ElementDetails initializeDiagram() {
        String f1 = vis.positionFields()[0];
        String f2 = position[1];

        out.comment("Define chord data structures");
        out.add("var chordData = BrunelData.diagram_Chord.make(processed,");

        // Always need from and to, but the size may be empty when building the chord data
        if (size == null) out.addQuoted(f1, f2);
        else out.addQuoted(f1, f2, size);
        out.add(")").endStatement();
        out.add("var chord = d3.layout.chord().padding(.025).sortSubgroups(d3.descending).matrix(chordData.matrix())").endStatement();

        // take arc path font size into account, adding a bit of padding, to define the arc width
        Size labelSize = ModelUtil.getAxisLabelFontSize(vis);
        double arcWidth = labelSize.valueInPixels(8) * 1.2;
        out.add("var arc_width =", Data.formatNumeric(arcWidth, false), ";").comment("Width of exterior arc");
        out.add("function keyFunction(d) { return d.source.index + '|' + d.target.index };").comment(" special key function for the edges");

        return ElementDetails.makeForDiagram(vis, ElementRepresentation.polygon, "edge", "chord.chords()");
    }

    public void writeDefinition(ElementDetails details) {

        // The Chords themselves are simple to create
        out.addChained("attr('d', d3.svg.chord().radius(geom.inner_radius-arc_width))")
                .addChained("attr('class', 'element " + element.name() + "')").endStatement();

        addAestheticsAndTooltips(details, true);

        // We now need to add the arcs on the outside for the groups
        out.onNewLine().ln().comment("Add in the arcs on the outside for the groups");
        out.add("diagramExtras.attr('class', 'diagram chord arcs')").endStatement();

        // The arcs themselves
        out.add("var arcGroup = diagramExtras.selectAll('path').data(chord.groups)").endStatement();
        out.add("arcGroup.enter().append('path').attr('class', 'box')").endStatement();
        out.add("BrunelD3.trans(arcGroup,transitionMillis)")
                .addChained("attr('d', d3.svg.arc().innerRadius(geom.inner_radius - arc_width).outerRadius(geom.inner_radius))")
                .addChained("attr('id', function(d, i) { return 'arc' + i; })").endStatement();

        out.add("var arcText = diagramExtras.selectAll('text').data(chord.groups)").endStatement();
        out.add("arcText.enter().append('text').attr('class', 'label')").endStatement();

        out.add("arcText.filter(function() { return !this.firstChild } )").comment("Only add paths if nothing yet added")
                .addChained("attr('dy', arc_width*0.72).attr('class', 'label')")
                .addChained("append('textPath').attr('xlink:href', function(d, i) { return '#arc' + i })")
                .addChained("text(function(d, i) { return chordData.group(i); })")
                .endStatement();
        out.add("BrunelD3.tween(arcText, transitionMillis,").indentMore().ln()
                .add("function(d,i) { var txt=this; return function() { BrunelD3.centerInWedge(txt, arcGroup[0][i], arc_width) } })")
                .endStatement();
    }

    public boolean needsDiagramExtras() {
        return true;
    }

    public void writeDiagramEnter() {
        // Ensure we have a row for each chord, based off the chord start and end points
        out.endStatement();
        out.add("selection.each(function(d) { d.row = chordData.index(d.target.index, d.target.subindex) })").endStatement();
    }
}
