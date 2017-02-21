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
import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.element.ElementBuilder;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.d3.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;

class Cloud extends D3Diagram {

    public Cloud(ElementStructure vis, Dataset data, D3Interaction interaction, ScriptWriter out) {
        super(vis, data, interaction, out);
    }

    public void writeDataStructures() {
        out.comment("Build the cloud layout");
        out.add("var cloud = BrunelD3.cloudLayout(processed, [geom.inner_width, geom.inner_height], zoomNode)").endStatement();
        out.add("function keyFunction(d) { return d.key }").endStatement();
    }

    public ElementDetails makeDetails(String commonSymbol) {
        return ElementDetails.makeForDiagram(vis, ElementRepresentation.text, "text", "data._rows");
    }

    public void writeDiagramUpdate(ElementDetails details) {
        out.addChained("each(cloud.prepare).call(cloud.build)");
        ElementBuilder.writeElementAesthetics(details, true, vis, out);
    }

    public void writeDiagramEnter(ElementDetails details) {
        // The cloud needs to set all this stuff up front
        out.addChained("style('text-anchor', 'middle').classed('label', true)")
                .addChained("text(labeling.content)");
        D3LabelBuilder.addFontSizeAttribute(vis, out);
    }

	public void writeLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
        ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}
}
