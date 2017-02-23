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

import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuildUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.auto.NumericScale;
import org.brunel.data.stats.DateStats;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.DateUnit;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Legends;

/**
 * Adds scales and axes; also guesses the right size to leave for axes
 *
 * IMPORTANT NOTE:
 *
 * The terms 'x' and 'y' both apply to the theoretical location. Whe we transpose a chart we move
 * axes and dimensions around, so we cannot always say 'x' runs horizontally. So in the code below
 * 'x' and 'y' are used only for the untransformed locations. We use left, right, top, bottom, h and v
 * for the transformed ones
 */
public class LegendBuilder {

	private final Field colorLegendField;           // Field to use for the color legend
	private final ChartStructure structure;
	private final ScriptWriter out;                 // Write definitions to here

	public LegendBuilder(ChartStructure structure, ScriptWriter out) {
		this.structure = structure;
		this.out = out;
		this.colorLegendField = getColorLegendField();
	}

	public boolean needsLegends() {
		return colorLegendField != null;
	}

	private Field getColorLegendField() {
		Field result = null;
		for (ElementStructure e : structure.elementStructure) {
			VisSingle vis = e.vis;
			boolean auto = vis.tLegends == Legends.auto;
			if (auto && structure.nested()) continue;                       // No default legend for nested charts
			if (vis.fColor.isEmpty()) continue;                            // No color means no color legend
			if (vis.tLegends == Legends.none) continue;                        // No legend if not asked for one

			Field f = e.data.field(vis.fColor.get(0).asField(e.data));        // Get the color filed
			if (auto && f.name.equals("#selection")) continue;              // No default legend for selection

			if (result == null) result = f;                                 // The first color definition
			else if (!same(result, f)) return null;                         // Two incompatible colors
		}
		return result;
	}

	public int legendWidth() {
		if (!needsLegends()) return 0;
		AxisRequirement legendRequirement = new AxisRequirement(VisTypes.Axes.none, -1);
		AxisDetails legendAxis = new AxisDetails(legendRequirement, new Field[]{colorLegendField}, colorLegendField.preferCategorical());
		legendAxis.setTextDetails(structure, false);
		int spaceNeededForTicks = 32 + legendAxis.maxCategoryWidth();
		int spaceNeededForTitle = colorLegendField.label.length() * 7;                // Assume 7 pixels per character
		return 6 + Math.max(spaceNeededForTicks, spaceNeededForTitle);                // Add some spacing
	}

	// Determine if position are the same
	private boolean same(Field a, Field b) {
		return a.name.equals(b.name) && a.preferCategorical() == b.preferCategorical();
	}

	public void writeLegends(VisSingle vis) {
		DateFormat dateFormat = null;
		if (vis.fColor.isEmpty() || colorLegendField == null) return;
		if (!vis.fColor.get(0).asField().equals(colorLegendField.name)) return;
		String legendTicks;
		if (colorLegendField.preferCategorical()) {
			// Categorical data can just grab it from the domain
			legendTicks = "scale_color.domain()";
			// Binned numeric data reads in opposite direction (bottom to top)
			if (colorLegendField.isBinned() && colorLegendField.isNumeric())
				legendTicks += ".reverse()";
		} else {
			// Numeric must calculate a nice range
			NumericScale details = Auto.makeNumericScale(colorLegendField, true, new double[]{0, 0}, 0.25, 7, false);
			Double[] divisions = details.divisions;
			if (details.granular) {
				// Granular data has divisions BETWEEN the values, not at them, so need to fix that
				Double[] newDiv = new Double[divisions.length - 1];
				for (int i = 0; i < newDiv.length; i++) newDiv[i] = (divisions[i] + divisions[i + 1]) / 2;
				divisions = newDiv;
			}
			// Reverse
			for (int i = 0; i < divisions.length / 2; i++) {
				Double t = divisions[divisions.length - 1 - i];
				divisions[divisions.length - 1 - i] = divisions[i];
				divisions[i] = t;
			}

			if (colorLegendField.isDate()) {
				// We cannot use the format for the date field, as it may be much more detailed than we need
				// We can instwad look at the difference between ticks to get the best format
				DateUnit dateUnit = DateStats.getUnit(Math.abs(divisions[divisions.length - 1] - divisions[0]));
				dateFormat = DateStats.getFormat(dateUnit, Math.abs(divisions[1] - divisions[0]));

				BuildUtil.DateBuilder dateBuilder = new BuildUtil.DateBuilder();
				String[] divs = new String[divisions.length];
				for (int i = 0; i < divs.length; i++)
					divs[i] = dateBuilder.make(Data.asDate(divisions[i]), dateFormat, true);

				legendTicks = "[" + Data.join(divs) + "]";
			} else {
				legendTicks = "[" + Data.join(divisions) + "]";
			}
		}

		String title = colorLegendField.label;
		if (title == null) title = colorLegendField.name;

		// Add the date format field in only for date legends
		out.add("BrunelD3.addLegend(legends, " + out.quote(title) + ", scale_color, " + legendTicks);
		if (dateFormat != null)
			out.add(", BrunelData.util_DateFormat." + dateFormat.name());
		out.add(")").endStatement();
	}

}
