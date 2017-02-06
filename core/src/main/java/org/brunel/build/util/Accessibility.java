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

package org.brunel.build.util;

import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.info.ElementStructure;
import org.brunel.data.Data;
import org.brunel.model.VisSingle;

/**
 * This class contains static methods that create content to enhance accessibility
 */
public class Accessibility {

    /**
     * Define a function to give text for a screen reader to read for an element.
     * This will be added as 'aria-label' and so will be read out.
     * The function is called on the selection and uses whichever of the following is defined (in priority order):
     * (1) tooltip (2) label (3) the intrinsic 'key fields' for the item
     *
     * @param structure    the element structure
     * @param out          writes to this
     * @param labelBuilder the element's label builder
     */
    private static void defineElementLabelFunction(ElementStructure structure, ScriptWriter out, D3LabelBuilder labelBuilder) {
        if (!structure.chart.accessible) return;
        VisSingle vis = structure.vis;
        out.onNewLine().add("function(d) { return ");
        if (!vis.itemsTooltip.isEmpty())
            labelBuilder.writeContent(vis.itemsTooltip, false);
        else if (!vis.itemsLabel.isEmpty())
            labelBuilder.writeContent(vis.itemsLabel, false);
        else {
            out.add("data._key(d.row)");
        }
        out.endStatement().add("}");
    }

    /**
     * Add to the selection the attributes needed to show aria labels
     *
     * @param structure the element structure
     * @param out       writes to this
     */
    public static void addAccessibilityLabels(ElementStructure structure, ScriptWriter out, D3LabelBuilder labelBuilder) {
        if (!structure.chart.accessible) return;
            out.addChained("attr('role', 'img').attr('aria-label', ");
        defineElementLabelFunction(structure, out, labelBuilder);
		out.add(")");
    }

    public static String makeNumberingTitle(String name, int index) {
        if (index == 0) return "First " + name;
        if (index == 1) return "Second " + name;
        if (index == 2) return "Third " + name;
        return name + " number " + (index + 1);
    }

    /**
     * Label a chart item as a region
     *
     * @param label name for it
     * @param out   writes to this
     */
    public static void writeLabelAttribute(String label, ScriptWriter out) {
        out.addChained("attr('role', 'region').attr('aria-label', " + Data.quote(label) + ")");
    }

    /**
     * Label an element group as a region
     *
     * @param structure the element structure
     * @param out       writes to this
     */
    public static void addElementInformation(ElementStructure structure, ScriptWriter out) {
        if (structure.chart.accessible)
            out.addChained("attr('role', 'region').attr('aria-label', "
                    + Data.quote(makeVisSingleLabel(structure.vis)) + ")");
    }

    protected static String makeVisSingleLabel(VisSingle vis) {

        String[] pos = vis.positionFields();
        String label = pos.length == 0 ? "No data" : Data.join(pos);

        if (vis.tDiagram != null)
            label += " as a " + vis.tDiagram + " diagram";
        else
            label += " as " + vis.tElement + "s";

        pos = vis.aestheticFields();
        if (pos.length > 0) label += ", also showing " + Data.join(pos);

        if (!vis.fSort.isEmpty()) label += ", sorted by " + Data.join(vis.fSort);

        return label.replaceAll("#", "");
    }
}
