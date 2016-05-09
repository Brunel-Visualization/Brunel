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

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;

/**
 * Defines titles
 */
public class D3TitleBuilder {
    private final String location;

    private String content;
    private String alignment;
    private int fontSize;

    /**
     * Builder for making titles
     *
     * @param structure chart structure to use
     * @param location  where to place the text
     */
    public D3TitleBuilder(ChartStructure structure, String location) {
        this.location = location;
        build(structure);
    }

    public double verticalSpace() {
        return content == null ? 0 : fontSize * 1.5;
    }

    public void writeContent(ScriptWriter out) {
        if (content == null) return;

        String xLoc = "50%", anchor = "middle";
        if (alignment.equals("left")) {
            xLoc = "0%";
            anchor = "start";
        }
        if (alignment.equals("right")) {
            xLoc = "100%";
            anchor = "end";
        }
        out.add("chart.append('text').attr('class', 'title " + location + "')")
                .add(".text(" + content + ").style('text-anchor', '" + anchor + "').attr('dy','0.8em')")
                .addChained("attr('x','" + xLoc + "')");

        if (location.equals("footer")) {
            out.addChained("attr('y','100%').attr('dy', '-0.4em')");
        } else {
            out.addChained("attr('y',0)");
        }

        out.endStatement();
    }

    // Search for the first title is the system and return it as text
    private void build(ChartStructure structure) {
        for (VisSingle vis : structure.elements) {
            if (vis.itemsTitle.isEmpty()) continue;
            String result = makeText(vis, vis.getDataset());
            content = result.isEmpty() ? null : Data.quote(result);
            alignment = ModelUtil.getTitlePosition(vis, location);
            ModelUtil.Size size = ModelUtil.getTitleSize(vis, location);
            if (size == null) fontSize = 16;
            else fontSize = (int) Math.round(size.valueInPixels(16));
        }
    }

    private String makeText(VisSingle vis, Dataset data) {
        StringBuilder s = new StringBuilder();
        for (Param p : vis.itemsTitle) {
            String pType = "header";                                        // The default
            if (p.hasModifiers()) pType = p.modifiers()[0].asString();      // Unless modified by a :footer or similar
            if (pType.equals(location)) {
                if (p.isField()) {
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
