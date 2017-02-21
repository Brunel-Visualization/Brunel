/*
 * Copyright (c) 2016 IBM Corporation and others.
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

package org.brunel.build.d3.element;

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

public class CoordinateElementBuilder extends ElementBuilder {

	public CoordinateElementBuilder(ElementStructure structure, ScriptWriter out, D3ScaleBuilder scales, D3Interaction interaction, D3Diagram diagram) {
		super(structure, interaction, scales, out, diagram);
	}

}
