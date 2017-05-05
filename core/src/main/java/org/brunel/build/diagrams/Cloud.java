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

package org.brunel.build.diagrams;

import org.brunel.action.Param;
import org.brunel.build.LabelBuilder;
import org.brunel.build.element.ElementBuilder;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

class Cloud extends D3Diagram {

	private final boolean rectangular;

	public Cloud(ElementStructure vis) {
		super(vis);
		this.rectangular = hasParam("rectangular");
	}

	private boolean hasParam(String s) {
		for (Param parameter : vis.tDiagramParameters)
			if (parameter.asString().equals(s)) return true;
		return false;
	}

	public void writeDataStructures(ScriptWriter out) {
		out.comment("Build the cloud layout");
		String rectParam = rectangular ? ", true" : "";
		out.add("var cloud = BrunelD3.cloudLayout(processed, [geom.inner_width, geom.inner_height]" + rectParam + ")").endStatement();
		out.add("function keyFunction(d) { return d.key }").endStatement();
	}

	public ElementDetails makeDetails() {
		return ElementDetails.makeForDiagram(structure, ElementRepresentation.text, "text", "data._rows");
	}

	public void writeDiagramUpdate(ElementDetails details, ScriptWriter out) {
		out.addChained("each(cloud.prepare).call(cloud.build)");
		ElementBuilder.writeElementAesthetics(details, true, vis, out);
	}

	public void writeDiagramEnter(ElementDetails details, LabelBuilder labelBuilder, ScriptWriter out) {
		// The cloud needs to set all this stuff up front
		out.addChained("style('text-anchor', 'middle').classed('label', true)");
		labelBuilder.setTextContentAndFontSize(out, vis);
	}

	public void writeLabelsAndTooltips(ElementDetails details, LabelBuilder labelBuilder) {
		ElementBuilder.writeElementLabelsAndTooltips(details, labelBuilder);
	}
}
