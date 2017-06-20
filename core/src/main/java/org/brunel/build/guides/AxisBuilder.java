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

package org.brunel.build.guides;

import org.brunel.build.ChartLocation;
import org.brunel.build.ScaleBuilder;
import org.brunel.build.ScalePurpose;
import org.brunel.build.info.ChartCoordinates;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.util.SVGGroupUtility;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.util.DateFormat;
import org.brunel.model.VisTypes;

/**
 * Build Axes for the chart
 */
public class AxisBuilder {

	private final ChartStructure structure;         // Overall detail on the chart composition
	private final ScriptWriter out;                 // Write definitions to here

	private final AxisDetails hAxis, vAxis;         // Details for each axis

	public AxisBuilder(ChartStructure structure, ScaleBuilder scalesBuilder, ScriptWriter out) {
		this.structure = structure;
		this.out = out;

		ChartCoordinates coords = structure.coordinates;

		AxisRequirement x = AxisRequirement.makeCombinedAxis(VisTypes.Axes.x, structure);
		AxisRequirement y = AxisRequirement.makeCombinedAxis(VisTypes.Axes.y, structure);
		AxisDetails xDetails = new AxisDetails(x, coords.allXFields, coords.xCategorical);
		AxisDetails yDetails = new AxisDetails(y, coords.allYFields, coords.yCategorical);

		hAxis = coords.isTransposed() ? yDetails : xDetails;
		vAxis = coords.isTransposed() ? xDetails : yDetails;

		hAxis.setTextDetails(structure, true);
		vAxis.setTextDetails(structure, false);

        /*
			We have a slight chicken-and-egg situation here. To layout any axis, we need to
            know the available space for it. But to do that we need to know the size of the
            color axis. But to do that we need to lay out the color axis ...
            To resolve this, we make a very simple guess for the horizontal axis, then
            layout the vertical axis based on that, then layout the horizontal
         */

		ChartLocation location = structure.location;

		vAxis.layoutVertically(location.getAvailableHeight() - hAxis.estimatedSimpleSizeWhenHorizontal());
		hAxis.layoutHorizontally(location.getAvailableWidth() - vAxis.size, scalesBuilder.elementsFillHorizontal(ScalePurpose.x));

		// Order is T L B R
		location.setAxisMargins(
				vAxis.topGutter,                                // Only the vAxis needs space here
				Math.max(vAxis.size, hAxis.leftGutter),            // Height of vAxis, or gutter for hAxis
				Math.max(hAxis.size, vAxis.bottomGutter),       // Height of hAxis, or gutter for vAxis
				hAxis.rightGutter                               // Only the hAxis needs space here
		);

	}

	public boolean needsAxes() {
		return hAxis.exists || vAxis.exists;
	}

	public void setAdditionalHAxisOffset(double v) {
		hAxis.setAdditionalHAxisOffset(v);
	}

	/**
	 * This method writes the code needed to define axes
	 */
	public void writeAxes() {
		if (!hAxis.exists && !vAxis.exists) return;                          // No axes needed

		// Define the spaces needed to work in

		// Define the groups for the axes and add titles
		if (hAxis.exists) {
			SVGGroupUtility groupUtil = new SVGGroupUtility(structure, "x_axis", out);
			out.onNewLine().add("axes.append('g').attr('class', 'x axis')")
					.addChained("attr('transform','translate(0,' + geom.inner_rawHeight + ')')");
			groupUtil.addClipPathReference("haxis");
			groupUtil.addAccessibleTitle("Horizontal Axis");
			out.endStatement();
			groupUtil.defineHorizontalAxisClipPath();

			// Add the title if necessary
			hAxis.writeTitle("axes.select('g.axis.x')", out);
		}
		if (vAxis.exists) {
			SVGGroupUtility groupUtil = new SVGGroupUtility(structure, "y_axis", out);
			out.onNewLine().add("axes.append('g').attr('class', 'y axis')");
			groupUtil.addClipPathReference("vaxis");
			groupUtil.addAccessibleTitle("Vertical Axis");
			out.endStatement();
			groupUtil.defineVerticalAxisClipPath();

			// Add the title if necessary
			vAxis.writeTitle("axes.select('g.axis.y')", out);

		}

		// Define the axes themselves and the method to build (and re-build) them
		out.onNewLine().ln();
		defineAxis("var axis_bottom = d3.axisBottom", this.hAxis, true);
		defineAxis("var axis_left = d3.axisLeft", this.vAxis, false);
		defineAxesBuild();
	}

	private void addRotateTicks() {
		out.add(".selectAll('.tick text')")
				.addChained("attr('transform', function() {")
				.indentMore().indentMore().onNewLine()
				.onNewLine().add("var v = this.getComputedTextLength() / Math.sqrt(2)/2;")
				.onNewLine().add("return 'translate(-' + (v+6) + ',' + v + ') rotate(-45)'")
				.indentLess().indentLess().onNewLine().add("})");

	}

	/**
	 * Adds the calls to set the axes into the already defined scale groups
	 */
	private void defineAxesBuild() {
		out.onNewLine().ln().add("function buildAxes(time) {").indentMore();
		if (hAxis.exists) {
			if (hAxis.categorical) {
				// Ensure the ticks are filtered so as not to overlap
				out.onNewLine().add("axis_bottom.tickValues(BrunelD3.filterTicks(" + hAxis.scaleName + "))");
			}
			out.onNewLine().add("var axis_x = axes.select('g.axis.x');");
			out.onNewLine().add("BrunelD3.transition(axis_x, time).call(axis_bottom.scale(" + hAxis.scaleName + "))");
			if (hAxis.rotatedTicks) addRotateTicks();
			out.endStatement();
		}

		if (vAxis.exists) {
			if (vAxis.categorical) {
				// Ensure the ticks are filtered so as not to overlap
				out.onNewLine().add("axis_left.tickValues(BrunelD3.filterTicks(" + vAxis.scaleName + "))");
			}

			out.onNewLine().add("var axis_y = axes.select('g.axis.y');");
			out.onNewLine().add("BrunelD3.transition(axis_y, time).call(axis_left.scale(" + vAxis.scaleName + "))");
			if (vAxis.rotatedTicks) addRotateTicks();
			out.endStatement();
		}

		if (hAxis.hasGrid)
			out.onNewLine()
					.add("BrunelD3.makeGrid(gridGroup, " + hAxis.scaleName + ", geom.inner_rawHeight, " + true + " )")
					.endStatement();
		if (vAxis.hasGrid)
			out.onNewLine()
					.add("BrunelD3.makeGrid(gridGroup, " + vAxis.scaleName + ", geom.inner_rawWidth, " + false + " )")
					.endStatement();
		out.indentLess().add("}").ln();
	}

	/**
	 * Defines an axis
	 *
	 * @param basicDefinition start of the line to generate
	 * @param axis            axis information
	 * @param horizontal      if the axis is horizontal
	 */
	private void defineAxis(String basicDefinition, AxisDetails axis, boolean horizontal) {
		if (axis.exists) {
			String transform = horizontal ? structure.coordinates.xTransform : structure.coordinates.yTransform;
			DateFormat dateFormat = horizontal ? structure.coordinates.xDateFormat : structure.coordinates.yDateFormat;

			// Do not define ticks by default
			String ticks;
			if (axis.tickCount != null) {
				ticks = Integer.toString(axis.tickCount);
			} else if (horizontal) {
				ticks = "Math.min(10, Math.round(geom.inner_rawWidth / " + (1.5 * axis.maxCategoryWidth()) + "))";
			} else {
				ticks = "Math.min(10, Math.round(geom.inner_rawHeight / 20))";
			}

			out.add(basicDefinition).add("(" + axis.scaleName + ").ticks(" + ticks);
			if (dateFormat != null)
				out.add(")");                                // No format needed
			else if ("log".equals(transform)) {
				if (axis.inMillions) out.add(", '0.0s')");    // format with no decimal places
				else out.add(", ',')");
			} else if (axis.inMillions)
				out.add(", 's')");                            // Units style formatting
			else
				out.add(")");                                // No formatting
			out.endStatement();
		}
	}

}
