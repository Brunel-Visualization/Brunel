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

import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Network extends D3Diagram {


    public Network(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
    }

    public boolean showsElement() {
        // Tree diagram shows element items at the tree locations
        return true;
    }

    public ElementDetails writeDataConstruction() {
        return ElementDetails.makeForDiagram("UNIMPLEMENTED", "circle", "box");
    }

    public void writeDefinition(ElementDetails details) {
    }
}
