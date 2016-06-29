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

import org.brunel.build.d3.AxisDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleTarget;

/**
 * Defines titles for axes
 */
public class AxisTitleBuilder extends TitleBuilder {

    private final AxisDetails axis;
    private final boolean isHorizontal;
    public double bottomOffset;                             // Extra offset to account for footers

    /**
     * For building an axis title
     *
     * @param vis    chart structure
     * @param axis         the axis info
     * @param isHorizontal true if the horizontal axis
     */
    public AxisTitleBuilder(VisSingle vis, AxisDetails axis, boolean isHorizontal) {
        super(vis, StyleTarget.makeTarget("text", axis.styleTarget, "title"));
        this.axis = axis;
        this.isHorizontal = isHorizontal;
    }

    protected String[] getXOffsets() {
        // Mismatch means we are transposed
        boolean transposed = axis.isX() != isHorizontal;

        if (isHorizontal) {
            String width = transposed ? "geom.inner_height" : "geom.inner_width";
            return new String[]{"0", width + "/2", width};
        } else {
            String height = transposed ? "-geom.inner_width" : "-geom.inner_height";
            return new String[]{height, height + "/2", "0"};
        }
    }

    protected void defineVerticalLocation(ScriptWriter out) {
        if (isHorizontal) {
            out.addChained("attr('y', geom.inner_bottom - " + (bottomOffset + padding.bottom) + ").attr('dy','-0.27em')");
        } else {
            out.addChained("attr('y', " + (2 + padding.top) + "-geom.inner_left).attr('dy', '0.7em').attr('transform', 'rotate(270)')");
        }
    }

    protected String makeText() {
        return axis.title;
    }

}
