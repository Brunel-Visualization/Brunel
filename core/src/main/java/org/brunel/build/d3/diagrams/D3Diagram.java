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

import org.brunel.action.Param;
import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.List;

public abstract class D3Diagram {
    public static D3Diagram make(ElementStructure structure, ScriptWriter out) {
        VisSingle vis = structure.vis;
        Dataset data = structure.data;
        if (vis.tDiagram == null) return null;
        if (vis.tDiagram == VisTypes.Diagram.bubble) return new Bubble(vis, data, out);
        if (vis.tDiagram == VisTypes.Diagram.chord) return new Chord(vis, data, out);
        if (vis.tDiagram == VisTypes.Diagram.cloud) return new Cloud(vis, data, out);
        if (vis.tDiagram == VisTypes.Diagram.tree) return new Tree(vis, data, out);
        if (vis.tDiagram == VisTypes.Diagram.treemap) return new Treemap(vis, data, out);
        if (vis.tDiagram == VisTypes.Diagram.network)
            return new Network(vis, data, structure, structure.chart.getEdge(), out);
        if (vis.tDiagram == VisTypes.Diagram.map) {
            if (vis.tDiagramParameters.length == 1 && vis.tDiagramParameters[0].asString().equals("labels"))
                return new GeoMapLabels(vis, data, structure.chart, out);
            else
                return new GeoMap(vis, data, structure.geo, out);
        }
        throw new IllegalStateException("Unknown diagram: " + vis.tDiagram);
    }

    final ScriptWriter out;
    final Param size;
    final VisTypes.Element element;
    final VisSingle vis;
    final D3LabelBuilder labelBuilder;
    final String[] position;
    private boolean isHierarchy = false;

    D3Diagram(VisSingle vis, Dataset data, ScriptWriter out) {
        this.vis = vis;
        this.out = out;
        this.size = vis.fSize.isEmpty() ? null : vis.fSize.get(0);
        this.position = vis.positionFields();
        this.element = vis.tElement;
        this.labelBuilder = new D3LabelBuilder(vis, out, data);

    }

    public String getRowKey() {
        return "d.key";
    }

    public String getStyleClasses() {
        // Define the classes for this group; note that we flag hierarchies specially also
        String classes = "diagram " + vis.tDiagram.name();
        return "'" + (isHierarchy ? classes + " hierarchy" : classes) + "'";
    }

    public boolean needsDiagramExtras() {
        // By default, no extras needed
        return false;
    }

    public boolean needsDiagramLabels() {
        // By default, no labels needed
        return false;
    }

    public void preBuildDefinitions() {
        // By default, do nothing
    }

    public void writeBuildCommands() {
        // By default, do nothing
    }

    public abstract ElementDetails writeDataConstruction();

    public abstract void writeDefinition(ElementDetails details, ElementDefinition elementDef);

    public void writeDiagramEnter() {
        // By default, nothing is needed
        out.endStatement();
    }

    public void writePerChartDefinitions() {
        // By Default, do nothing
    }

    public void writePreDefinition(ElementDetails details, ElementDefinition elementDef) {
        // By Default, do nothing
    }

    void addAestheticsAndTooltips(ElementDetails details, boolean addLabels) {
        String remapAesthetics = "if (d == null || d.row == null) return; ";
        if (!vis.itemsTooltip.isEmpty()) {
            labelBuilder.addTooltips(details);
        }

        if (!vis.fColor.isEmpty())
            out.add("selection.style('fill', function(d) { " + remapAesthetics + "return color(d) })").endStatement();

        if (!vis.fOpacity.isEmpty())
            out.add("selection.style('fill-opacity', function(d) { " + remapAesthetics + "return opacity(d) })")
                    .addChained("style('stroke-opacity', function(d) { " + remapAesthetics + "return opacity(d) })")
                    .endStatement();

        if (addLabels) labelBuilder.addElementLabeling();

    }

    void makeHierarchicalTree() {
        String[] positionFields = vis.positionFields();
        String fieldsList = positionFields.length == 0 ? "" : ", " + quoted(positionFields);
        String sizeParam = size == null ? null : Data.quote(size.asField());
        out.add("var tree = BrunelData.diagram_Hierarchical.makeByNestingFields(processed, " + sizeParam + fieldsList + ")").endStatement();
        isHierarchy = true;
    }

    protected String quoted(String... items) {
        List<String> p = new ArrayList<String>();
        for (String s : items) p.add(Data.quote(s));
        return Data.join(p);
    }

}

