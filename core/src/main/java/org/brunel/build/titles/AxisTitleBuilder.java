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

package org.brunel.build.titles;

import org.brunel.build.AxisDetails;
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
        if (isHorizontal) {
            return new String[]{"0", "geom.inner_rawWidth/2", "geom.inner_rawWidth"};
        } else {
            return new String[]{"-geom.inner_rawHeight", "-geom.inner_rawHeight/2", "0"};
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
