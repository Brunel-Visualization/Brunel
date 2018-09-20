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

import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisElement;
import org.brunel.model.style.StyleTarget;

/**
 * Defines titles for axes
 */
class AxisTitleBuilder extends TitleBuilder {

  private final AxisDetails axis;
  private final boolean isHorizontal;
  private final boolean isOpposite;
  public double bottomOffset;                             // Extra offset to account for footers

  /**
   * For building an axis title
   *
   * @param vis          chart structure
   * @param axis         the axis info
   * @param isOpposite   true if we are an opposite y axis
   * @param isHorizontal true if the horizontal axis
   */
  public AxisTitleBuilder(VisElement vis, AxisDetails axis, boolean isHorizontal, boolean isOpposite) {
    super(vis, StyleTarget.makeTarget("text", axis.styleTarget, "title"));
    this.axis = axis;
    this.isHorizontal = isHorizontal;
    this.isOpposite = isOpposite;
  }

  protected void defineVerticalLocation(ScriptWriter out) {
    if (isHorizontal) {
      out.addChained("attr('y', geom.inner_bottom - " + (bottomOffset + padding.bottom) + ").attr('dy','-0.27em')");
    } else if (isOpposite) {
      out.addChained("attr('y', " + (2 + padding.top) + "-geom.inner_right).attr('dy', '0.7em').attr('transform', 'rotate(90)')");
    } else {
      out.addChained("attr('y', " + (2 + padding.top) + "-geom.inner_left).attr('dy', '0.7em').attr('transform', 'rotate(270)')");
    }
  }

  protected String makeText() {
    return axis.title;
  }

  protected String[] getXOffsets() {
    if (isHorizontal) {
      return new String[]{"0", "geom.inner_rawWidth/2", "geom.inner_rawWidth"};
    } else if (isOpposite) {
      return new String[]{"geom.inner_rawHeight", "geom.inner_rawHeight/2", "0"};
    } else {
      return new String[]{"-geom.inner_rawHeight", "-geom.inner_rawHeight/2", "0"};
    }
  }

}
