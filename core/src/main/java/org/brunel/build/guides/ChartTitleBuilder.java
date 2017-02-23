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

package org.brunel.build.guides;

import org.brunel.action.Param;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleTarget;

/**
 * Defines titles
 */
public class ChartTitleBuilder extends TitleBuilder {

    private final String location;          // "header" or  "footer"

    /**
     * Builder for making titles
     *
     * @param structure chart structure to use
     * @param location  where to place the text
     */
    public ChartTitleBuilder(ChartStructure structure, String location) {
        super(findTitleElement(structure), StyleTarget.makeTopLevelTarget(null, "title", location));
        this.location = location;
    }

    /**
     * Search for the first element with a title defined
     *
     * @param structure chart definition
     * @return titled element, or null
     */
    private static VisSingle findTitleElement(ChartStructure structure) {
        for (VisSingle vis : structure.elements) {
            if (!vis.itemsTitle.isEmpty()) return vis;
        }
        return structure.elements[0];
    }

    protected void defineVerticalLocation(ScriptWriter out) {
        if ("header".equals(location)) {
            out.addChained("attr('y'," + padding.top + ").attr('dy','0.8em')");
        } else {
            out.addChained("attr('y','100%').attr('dy', -" + (padding.bottom + fontSize * 4 / 10) + ")");
        }
    }

    protected String makeText() {
        if (vis == null) return null;
        StringBuilder s = new StringBuilder();
        for (Param p : vis.itemsTitle) {
            String pType = "header";                                        // The default
            if (p.hasModifiers()) pType = p.modifiers()[0].asString();      // Unless modified by a :footer or similar
            if (pType.equals(location)) {
                if (p.isField()) {
                    Dataset data = vis.getDataset();
                    Field f = data.field(p.asField(data));
                    if (f == null) throw new IllegalStateException("Unknown field: " + p.asString());
                    else s.append(f.label);
                } else {
                    s.append(p.asString());
                }
            }
        }
        return s.toString();
    }

}
