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

import org.brunel.build.chart.ChartStructure;
import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Network extends D3Diagram {

    private final ChartStructure dependency;

    public Network(VisSingle vis, Dataset data, ChartStructure dependency, ScriptWriter out) {
        super(vis, data, out);
        this.dependency = dependency;
    }

    public ElementDetails writeDataConstruction() {
        String nodeField = quoted(vis.fKeys.get(0).asField());

        VisSingle links = dependency.getEdgeElement();
        String edgeDataset = dependency.linkedDataReference(links);
        String a = quoted(links.fKeys.get(0).asField());
        String b = quoted(links.fKeys.get(1).asField());

        out.add("chart.graph = BrunelData.diagram_Graph.make(processed,", nodeField, ",",
                edgeDataset, ",", a, ",", b, ")").endStatement();
        makeLayout();
        return ElementDetails.makeForDiagram("chart.graph.nodes", "circle", "point", "box", false);
    }

    private void makeLayout() {
        out.comment("Initial Circle Layout")
                .add("var g = chart.graph, r1 = geom.inner_width/2, r2 = geom.inner_height/2,").onNewLine()
                .add("a, i, N = g.nodes.length").endStatement()
                .add("for(i=0; i<N; i++) { a = Math.PI*2*i/N; g.nodes[i].x = r1 + 0.75*r1*Math.cos(a); g.nodes[i].y = r2 + 0.75*r2*Math.sin(a) }")
                .endStatement();
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.addChained("attr('r',", elementDef.overallSize, ")").endStatement();
        addAestheticsAndTooltips(details, true);
    }

}
