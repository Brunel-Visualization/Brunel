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

package org.brunel.build.d3.titles;

import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.model.VisSingle;

/**
 * An abstract class for a builder to construct text content for titles.
 * Handles singles lines of text only at present
 */
public abstract class TitleBuilder {
    protected final VisSingle vis;
    private final String alignment;
    private final int fontSize;
    private final String[] classes;
    private String content;

    /**
     * An abstract class for a builder to construct text content for titles
     * Used for char titles, axis titles and legend titles
     *
     * @param vis     the element that defines the item
     * @param classes the css classes to examine for style info
     */
    public TitleBuilder(VisSingle vis, String[] parentClasses, String... classes) {
        this.vis = vis;
        this.classes = classes;
        alignment = ModelUtil.getTitlePosition(vis, parentClasses, classes);
        ModelUtil.Size size = ModelUtil.getTitleSize(vis, parentClasses, classes);
        if (size == null) fontSize = 16;
        else fontSize = (int) Math.round(size.valueInPixels(16));
    }

    public String content() {
        if (content == null) {
            String s = makeText();
            content = (s == null || s.isEmpty()) ? null : Data.quote(s);
        }
        return content;
    }

    /**
     * Write the title content
     *
     * @param group the owning group (D3 selection) into which to place the text
     * @param out   writer
     */
    public void writeContent(String group, ScriptWriter out) {
        if (content() == null) return;

        out.add(group + ".append('text').attr('class', '" + Data.join(classes, " ") + "')")
                .add(".text(" + content() + ")");
        defineHorizontalLocation(out);
        defineVerticalLocation(out);
        out.endStatement();
    }

    private void defineHorizontalLocation(ScriptWriter out) {
        String[] xOffsets = getXOffsets();

        String xLoc, anchor;
        if (alignment.equals("left")) {
            xLoc = xOffsets[0];
            anchor = "start";
        } else if (alignment.equals("right")) {
            xLoc = xOffsets[2];
            anchor = "end";
        } else {
            xLoc = xOffsets[1];
            anchor = "middle";
        }
        out.add(".style('text-anchor', '" + anchor + "')")
                .addChained("attr('x'," + xLoc + ")");
    }

    protected String[] getXOffsets() {
        // The defaults are simply in terms of the enclosing object
        return new String[]{"'0%'", "'50%'", "'100%'"};
    }

    protected abstract void defineVerticalLocation(ScriptWriter out);

    public double verticalSpace() {
        return content() == null ? 0 : fontSize;
    }

    protected abstract String makeText();
}
