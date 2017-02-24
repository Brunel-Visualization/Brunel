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

import org.brunel.action.Param;
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

import java.util.Arrays;
import java.util.List;

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

	private final Field colorField;            // Field to use for the color legend
	private final Field symbolField;            // Field to use for the symbol legend
	private final ChartStructure structure;
	private final ScriptWriter out;                 // Write definitions to here

	public LegendBuilder(ChartStructure structure, ScriptWriter out) {
		this.structure = structure;
		this.out = out;
		this.colorField = getTargetField("color", null);
		this.symbolField = getTargetField("symbol", colorField);
	}

	/**
	 * Return true if we need a legend
	 *
	 * @return true if one is needed
	 */
	public boolean needsLegends() {
		return colorField != null || symbolField != null;
	}

	/**
	 * Search through all the elements for the field for this aesthetic.
	 * Since we only show one legend, we must ensure that the field is representative of all the
	 * other fields with the same name, and, when we combine legends for color and symbol, with the other one
	 *
	 * @param aestheticName  the name of the aesthetic: "color" or "symbol"
	 * @param requiredSameAs a field we must be the same as
	 * @return a good field to use, or non.
	 */
	private Field getTargetField(String aestheticName, Field requiredSameAs) {
		Field result = null;
		for (ElementStructure e : structure.elementStructure) {
			VisSingle vis = e.vis;
			if (vis.tLegends == Legends.none)    // No legend for this element, so skip this element
				continue;
			boolean auto = vis.tLegends == Legends.auto;                // Are we automatic?
			if (auto && structure.nested()) continue;                   // No default legend for nested charts
			Field aesthetic = getAestheticField(e, aestheticName);      // Find the aesthetic
			if (aesthetic == null) continue;                            // No aesthetic means no  legend
			if (auto && aesthetic.name.equals("#selection")) continue;  // No automatic legend for selection

			// If we match other found fields and the required match, we are good. Otherwise no match possible
			if (same(aesthetic, result) && same(aesthetic, requiredSameAs))
				result = aesthetic;
			else
				return null;
		}
		return result;
	}

	private Field getAestheticField(ElementStructure e, String aestheticName) {
		// The choices for legends are either color or symbol
		List<Param> params = aestheticName.equals("color") ? e.vis.fColor : e.vis.fSymbol;
		if (params.isEmpty()) return null;
		return e.data.field(params.get(0).asField(e.data));        // Get the target field
	}

	public int legendWidth() {
		if (!needsLegends()) return 0;
		AxisRequirement legendRequirement = new AxisRequirement(VisTypes.Axes.none, -1);
		AxisDetails legendAxis = new AxisDetails(legendRequirement, new Field[]{colorField}, colorField.preferCategorical());
		legendAxis.setTextDetails(structure, false);
		int spaceNeededForTicks = 32 + legendAxis.maxCategoryWidth();
		int spaceNeededForTitle = colorField.label.length() * 7;                // Assume 7 pixels per character
		return 6 + Math.max(spaceNeededForTicks, spaceNeededForTitle);                // Add some spacing
	}

	// Determine if the fields are the same. Note that they might be different summaries with the same actual
	// results -- we really only care about their domains and names
	private boolean same(Field a, Field b) {
		if (a == null || b == null) return true;
		if (!a.name.equals(b.name)) return false;
		if (a.preferCategorical())
			return Arrays.equals(a.categories(), b.categories());
		else if (b.preferCategorical())
			return false;
		else return (Math.abs(a.min() - b.min()) + Math.abs(a.max() - b.max()) > 1e-6);
	}

	/**
	 * This is called to set the legend information for the chart (which has multiple elements).
	 * Only the elements that define the right fields add their scale to the chart
	 *
	 * @param e the element we are writing at the moment
	 */
	public void defineUsageForLegend(ElementStructure e) {
		if (colorField == getAestheticField(e, "color")) {

			DateFormat dateFormat = null;
			String legendTicks;
			if (colorField.preferCategorical()) {
				// Categorical data can just grab it from the domain
				legendTicks = getTargetScaleName() + ".domain()";
				// Binned numeric data reads in opposite direction (bottom to top)
				if (colorField.isBinned() && colorField.isNumeric())
					legendTicks += ".reverse()";
			} else {
				// Numeric must calculate a nice range
				NumericScale details = Auto.makeNumericScale(colorField, true, new double[]{0, 0}, 0.25, 7, false);
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

				if (colorField.isDate()) {
					// We cannot use the format for the date field, as it may be much more detailed than we need
					// We can instead look at the difference between ticks to get the best format
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

			String title = colorField.label;
			if (title == null) title = colorField.name;

			out.onNewLine().add("legends._legend = legends._legend || { title: ")
					.add(Data.quote(title)).add(", ticks: ").add(legendTicks).add("}").endStatement()
					.add("legends._legend.color = scale_color").endStatement();

			if (dateFormat != null)
				out.add("legends._legend.dateFormat = BrunelData.util_DateFormat." + dateFormat.name())
						.endStatement();

		}
	}

	/**
	 * This is called at the end of the chart building section to write the legends out
	 */
	public void writeLegends() {
		if (needsLegends()) out.onNewLine().add("BrunelD3.addLegend(legends, legends._legend)").endStatement();
	}

	private String getTargetScaleName() {
		return "scale_color";
	}

}
