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

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.action.Param.Type;
import org.brunel.build.d3.D3Util.DateBuilder;
import org.brunel.build.info.ChartCoordinates;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.color.ColorMapping;
import org.brunel.color.Palette;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.auto.Auto;
import org.brunel.data.auto.NumericScale;
import org.brunel.data.stats.DateStats;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.DateUnit;
import org.brunel.data.util.Range;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Axes;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;
import org.brunel.model.VisTypes.Legends;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import static org.brunel.build.d3.ScalePurpose.color;

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
public class D3ScaleBuilder {

	private static final double MIN_SIZE_FACTOR = 0.001;

	final Coordinates coords;                       // Combined coordinate system derived from all elements
	private final Field colorLegendField;           // Field to use for the color legend
	private final AxisDetails hAxis, vAxis;         // Details for each axis
	private final double[] marginTLBR;              // Margins between the coordinate area and the chart space
	private final ChartStructure structure;         // Overall detail on the chart composition
	private final VisSingle[] elements;             // The elements that define the scales used
	private final ScriptWriter out;                 // Write definitions to here

	public D3ScaleBuilder(ChartStructure structure, ScriptWriter out) {
		this.structure = structure;
		this.elements = structure.elements;
		this.out = out;
		this.coords = makeCombinedCoords();
		AxisSpec[] axes = makeCombinedAxes();

		// Create the position needed
		this.colorLegendField = getColorLegendField();

		ChartCoordinates coords = structure.coordinates;

		// Set the axis information for each dimension
		AxisDetails xAxis, yAxis;
		if (axes[0] != null) {
			xAxis = new AxisDetails("x", coords.allXFields, coords.xCategorical, axes[0].name, axes[0].ticks, axes[0].grid);
		} else
			xAxis = new AxisDetails("x", new Field[0], coords.xCategorical, null, 9999, false);
		if (axes[1] != null)
			yAxis = new AxisDetails("y", coords.allYFields, coords.yCategorical, axes[1].name, axes[1].ticks, axes[1].grid);
		else
			yAxis = new AxisDetails("y", new Field[0], coords.yCategorical, null, 9999, false);

		// Map the dimension to the physical location on screen
		if (this.coords == Coordinates.transposed) {
			hAxis = yAxis;
			vAxis = xAxis;
		} else {
			hAxis = xAxis;
			vAxis = yAxis;
		}

		hAxis.setTextDetails(structure, true);
		vAxis.setTextDetails(structure, false);

		int legendWidth = legendWidth();

        /*
			We have a slight chicken-and-egg situation here. To layout any axis, we need to
            know the available space for it. But to do that we need to know the size of the
            color axis. But to do that we need to lay out the color axis ...
            To resolve this, we make a very simple guess for the horizontal axis, then
            layout the vertical axis based on that, then layout the horizontal
         */

		vAxis.layoutVertically(structure.chartHeight - hAxis.estimatedSimpleSizeWhenHorizontal());
		hAxis.layoutHorizontally(structure.chartWidth - vAxis.size - legendWidth, elementsFillHorizontal(ScalePurpose.x));

		// Set the margins
		int marginTop = vAxis.topGutter;                                    // Only the vAxis needs space here
		int marginLeft = Math.max(vAxis.size, hAxis.leftGutter);            // Width of vAxis, or horizontal gutter
		int marginBottom = Math.max(hAxis.size, vAxis.bottomGutter);        // Height of hAxis, or gutter for vAxis
		int marginRight = Math.max(hAxis.rightGutter, legendWidth);         // Overflow for hAxis, or legend
		marginTLBR = new double[]{marginTop, marginLeft, marginBottom, marginRight};
	}

	public boolean needsLegends() {
		return colorLegendField != null;
	}

	public void setAdditionalHAxisOffset(double v) {
		hAxis.setAdditionalHAxisOffset(v);
	}

	private Coordinates makeCombinedCoords() {
		// For diagrams, we set the coords to polar for the chord chart and clouds, and centered for networks
		if (structure.diagram == Diagram.chord || structure.diagram == Diagram.cloud)
			return Coordinates.polar;

		// The rule here is that we return the one with the highest ordinal value;
		// that will correspond to the most "unusual". In practice this means that
		// you need only define 'polar' or 'transpose' in one chart
		Coordinates result = elements[0].coords;
		for (VisSingle e : elements) if (e.coords.compareTo(result) > 0) result = e.coords;

		return result;
	}

	// Return array for X and Y dimensions
	private AxisSpec[] makeCombinedAxes() {

		AxisSpec x = null;
		AxisSpec y = null;

		boolean auto = true;

		// Rules:
		// none overrides everything and no axes are used
		// auto or no parameters means that we want default axes for this chart
		// x or y means that we wish to define just that axis

		// The rule here is that we add axes as much as possible, so presence overrides lack of presence
		for (VisSingle e : elements) {
			if (e.tDiagram != null || e.fAxes.containsKey(Axes.none)) {
				// return two null specs -- we do not want axes
				return new AxisSpec[2];
			}

			for (Entry<Axes, Param[]> p : e.fAxes.entrySet()) {
				auto = false;
				Axes key = p.getKey();
				Param[] value = p.getValue();
				if (key == Axes.x) x = (x == null ? AxisSpec.DEFAULT : x).merge(value);
				else if (key == Axes.y) y = (y == null ? AxisSpec.DEFAULT : y).merge(value);
			}
		}

		// If auto, check for the coordinate system / diagram / nesting to determine what is wanted
		if (auto) if (coords == Coordinates.polar || structure.diagram != null || structure.nested())
			return new AxisSpec[2];
		else
			return new AxisSpec[]{AxisSpec.DEFAULT, AxisSpec.DEFAULT};

		return new AxisSpec[]{x, y};
	}

	private Field getColorLegendField() {
		Field result = null;
		for (VisSingle vis : elements) {
			boolean auto = vis.tLegends == Legends.auto;
			if (auto && structure.nested()) continue;                       // No default legend for nested charts
			if (vis.fColor.isEmpty()) continue;                             // No color means no color legend
			if (vis.tLegends == Legends.none) continue;            // No legend if not asked for one

			Field f = fieldById(getColor(vis).asField(), vis);
			if (auto && f.name.equals("#selection")) continue;              // No default legend for selection

			if (result == null) result = f;                                 // The first color definition
			else if (!same(result, f)) return null;                         // Two incompatible colors
		}
		return result;
	}

	private int legendWidth() {
		if (!needsLegends()) return 0;
		AxisDetails legendAxis = new AxisDetails("color", new Field[]{colorLegendField}, colorLegendField.preferCategorical(), null, 9999, false);
		legendAxis.setTextDetails(structure, false);
		int spaceNeededForTicks = 32 + legendAxis.maxCategoryWidth();
		int spaceNeededForTitle = colorLegendField.label.length() * 7;                // Assume 7 pixels per character
		return 6 + Math.max(spaceNeededForTicks, spaceNeededForTitle);                // Add some spacing
	}

	private boolean elementsFillHorizontal(ScalePurpose purpose) {
		for (VisSingle e : elements) {
			// All must be lines or areas to fill to the edge
			if (e.tElement != Element.line && e.tElement != Element.area) return false;
			// There must be no clustering on the X axis
			if (purpose == ScalePurpose.x && e.fX.size() > 1) return false;
		}

		return true;
	}

	private Field fieldById(String fieldName, VisSingle vis) {
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] == vis) {
				Field field = structure.elementStructure[i].data.field(fieldName);
				if (field == null) throw new IllegalStateException("Unknown field " + fieldName);
				return field;
			}
		}
		throw new IllegalStateException("Passed in a vis that was not part of the system defined in the constructor");
	}

	private Param getColor(VisSingle vis) {
		return vis.fColor.isEmpty() ? null : vis.fColor.get(0);
	}

	private Param getSymbol(VisSingle vis) {
		return vis.fSymbol.isEmpty() ? null : vis.fSymbol.get(0);
	}

	// Determine if position are the same
	private boolean same(Field a, Field b) {
		return a.name.equals(b.name) && a.preferCategorical() == b.preferCategorical();
	}

	public boolean allNumeric(Field[] fields) {
		for (Field f : fields)
			if (!f.isNumeric())
				return false;
		return true;
	}

	public Double getGranularitySuitableForSizing(Field[] ff) {
		Double r = null;
		for (Field f : ff) {
			if (f.isDate()) continue;   // No date granularity use
			Double g = f.numProperty("granularity");
			if (g != null && g / (f.max() - f.min()) > 0.02) {
				if (r == null || g < r) r = g;
			}
		}
		return r;
	}

	public double[] marginsTLBR() {
		return this.marginTLBR;
	}

	public boolean needsAxes() {
		return hAxis.exists() || vAxis.exists();
	}

	public void writeAestheticScales(ElementStructure structure) {
		VisSingle vis = structure.vis;

		// Some node structures have the data within a 'data' fields instead of at the top level
		boolean dataInside = structure.hasHierarchicalData() && !structure.isDependent();

		Param color = getColor(vis);
		Param symbol = getSymbol(vis);
		Param[] size = getSize(vis);
		Param[] css = getCSSAesthetics(vis);
		Param opacity = getOpacity(vis);
		if (color == null && opacity == null && size.length == 0 && css.length == 0) return;

		out.onNewLine().comment("Aesthetic Functions");
		if (color != null) {
			addColorScale(color, vis);
			Field field = fieldById(color, vis);
			out.onNewLine().add("var color = function(d) { return scale_color(" + D3Util.writeCall(field, dataInside) + ") }").endStatement();
		}
		if (opacity != null) {
			addOpacityScale(opacity, vis);
			Field field = fieldById(opacity, vis);
			out.onNewLine().add("var opacity = function(d) { return scale_opacity(" + D3Util.writeCall(field, dataInside) + ") }").endStatement();
		}
		if (symbol != null) {
			addSymbolScale(symbol, structure);
			Field field = fieldById(symbol, vis);
			out.onNewLine().add("var symbolID = function(d) { return scale_symbol(" + D3Util.writeCall(field, dataInside) + ") }").endStatement();
		}
		for (int i = 0; i < css.length; i++) {
			Param p = css[i];
			String suffix = css.length > 1 ? "_" + (i + 1) : "";                    // Add a suffix for multiple class aesthetics only
			defineCSSClassAesthetic(vis, dataInside, p, suffix);
		}

		if (size.length == 1) {
			// We have exactly one field and util that for the single size scale, with a root transform by default for point elements
			String defaultTransform = (vis.tElement == Element.point || vis.tElement == Element.text)
					? "sqrt" : "linear";
			addSizeScale("size", size[0], vis, defaultTransform);
			Field field = fieldById(size[0], vis);
			out.onNewLine().add("var size = function(d) { return scale_size(" + D3Util.writeCall(field, dataInside) + ") }").endStatement();
		} else if (size.length > 1) {
			// We have two field and util them for height and width
			addSizeScale("width", size[0], vis, "linear");
			addSizeScale("height", size[1], vis, "linear");
			Field widthField = fieldById(size[0], vis);
			out.onNewLine().add("var width = function(d) { return scale_width(" + D3Util.writeCall(widthField, dataInside) + ") }").endStatement();
			Field heightField = fieldById(size[1], vis);
			out.onNewLine().add("var height = function(d) { return scale_height(" + D3Util.writeCall(heightField, dataInside) + ") }").endStatement();
		}
	}

	private void defineCSSClassAesthetic(VisSingle vis, boolean dataInside, Param p, String suffix) {
		Field field = fieldById(p, vis);

		// Define the prefix for the classes we will use
		String cssPrefix = p.hasModifiers() ? p.firstModifier().asString() : "brunel_class_";

		// Only use names if specifically requested
		boolean useNames = p.modifiers().length > 1 && p.modifiers()[1].asString().equals("names");

		if (useNames) {
			// This is easy as we have no need for a scale -- we just use the raw values coming from the field
			out.onNewLine().add("var css" + suffix + " = function(d) { return " + Data.quote(cssPrefix) + " + "
					+ D3Util.writeCall(field, dataInside) + " }").endStatement();
			return;
		}

		// Define the scale
		out.add("var scale_css" + suffix + " = ");

		if (field.preferCategorical()) {
			// Each category is mapped to 1,2,3, etc.
			int categories = makeCategoricalScale(new Field[]{field}, ScalePurpose.color, false);
			String[] indices = new String[categories];
			for (int i = 0; i < indices.length; i++) indices[i] = Data.quote(cssPrefix + (i + 1));
			out.addChained("range(" + Arrays.toString(indices) + ")");
		} else {
			// Divide into two categories
			out.add("d3.scaleQuantize().domain([" + field.min() + ", " + field.max() + "]).range(["
					+ Data.quote(cssPrefix + "1") + ", " + Data.quote(cssPrefix + "2") + "])");
		}

		out.endStatement()
				.add("var css" + suffix + " = function(d) { return scale_css" + suffix + "("
						+ D3Util.writeCall(field, dataInside) + ") }").endStatement();
	}

	/**
	 * This method writes the code needed to define axes
	 */
	public void writeAxes() {
		if (!hAxis.exists() && !vAxis.exists()) return;                          // No axes needed

		// Define the spaces needed to work in

		// Define the groups for the axes and add titles
		if (hAxis.exists()) {
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
		if (vAxis.exists()) {
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

	/**
	 * Defines an axis
	 *
	 * @param basicDefinition start of the line to generate
	 * @param axis            axis information
	 * @param horizontal      if the axis is horizontal
	 */
	public void defineAxis(String basicDefinition, AxisDetails axis, boolean horizontal) {
		if (axis.exists()) {
			String transform = horizontal ? structure.coordinates.xTransform : structure.coordinates.yTransform;
			DateFormat dateFormat = horizontal ? structure.coordinates.xDateFormat : structure.coordinates.yDateFormat;

			// Do not define ticks by default
			String ticks;
			if (axis.tickCount != null) {
				ticks = Integer.toString(axis.tickCount);
			} else if (horizontal) {
				ticks = "Math.min(10, Math.round(geom.inner_width / " + (1.5 * axis.maxCategoryWidth()) + "))";
			} else {
				ticks = "Math.min(10, Math.round(geom.inner_width / 20))";
			}

			out.add(basicDefinition).add("(" + axis.scale + ").ticks(" + ticks);
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

	/**
	 * Adds the calls to set the axes into the already defined scale groups
	 */
	private void defineAxesBuild() {
		out.onNewLine().ln().add("function buildAxes(time) {").indentMore();
		if (hAxis.exists()) {
			if (hAxis.categorical) {
				// Ensure the ticks are filtered so as not to overlap
				out.onNewLine().add("axis_bottom.tickValues(BrunelD3.filterTicks(" + hAxis.scale + "))");
			}
			out.onNewLine().add("var axis_x = axes.select('g.axis.x');");
			out.onNewLine().add("BrunelD3.transition(axis_x, time).call(axis_bottom.scale(" + hAxis.scale + "))");
			if (hAxis.rotatedTicks) addRotateTicks();
			out.endStatement();
		}

		if (vAxis.exists()) {
			if (vAxis.categorical) {
				// Ensure the ticks are filtered so as not to overlap
				out.onNewLine().add("axis_left.tickValues(BrunelD3.filterTicks(" + vAxis.scale + "))");
			}

			out.onNewLine().add("var axis_y = axes.select('g.axis.y');");
			out.onNewLine().add("BrunelD3.transition(axis_y, time).call(axis_left.scale(" + vAxis.scale + "))");
			if (vAxis.rotatedTicks) addRotateTicks();
			out.endStatement();
		}

		// The gridlines are with an untransposed group, which makes this logic
		// much harder -- the 'hAxis' is on the horizontal, but it could be for the
		// 'y' scale, and the widths are transposed, so they need inverting (when
		// we want the transposed height, we as for the width)

		if (hAxis.hasGrid) {
			if (hAxis.isX()) addGrid("scale_x", "geom.inner_height", true);
			else addGrid("scale_y", "geom.inner_width", true);
		}
		if (vAxis.hasGrid) {
			if (vAxis.isX()) addGrid("scale_x", "geom.inner_height", false);
			else addGrid("scale_y", "geom.inner_width", false);
		}

		out.indentLess().add("}").ln();
	}

	private void addGrid(String scaleName, String extent, boolean isX) {
		out.onNewLine().add("BrunelD3.makeGrid(gridGroup, " + scaleName + ", " + extent + ", " + isX + " )")
				.endStatement();
	}

	private void addRotateTicks() {
		out.add(".selectAll('.tick text')")
				.addChained("attr('transform', function() {")
				.indentMore().indentMore().onNewLine()
				.onNewLine().add("var v = this.getComputedTextLength() / Math.sqrt(2)/2;")
				.onNewLine().add("return 'translate(-' + (v+6) + ',' + v + ') rotate(-45)'")
				.indentLess().indentLess().onNewLine().add("})");

	}

	public void writeCoordinateScales() {
		ChartCoordinates coordinates = structure.coordinates;
		writePositionScale(ScalePurpose.x, coordinates.allXFields, getXRange(), elementsFillHorizontal(ScalePurpose.x), coordinates.xReversed);
		writePositionScale(ScalePurpose.inner, coordinates.allXClusterFields, "[-0.5, 0.5]", elementsFillHorizontal(ScalePurpose.inner), coordinates.xReversed);
		writePositionScale(ScalePurpose.y, coordinates.allYFields, getYRange(), false, coordinates.yReversed);
		writeScaleExtras();
	}

	protected void writeScaleExtras() {
		out.onNewLine().add("var base_scales = [scale_x, scale_y];").at(50).comment("Untransformed original scales");
		writeAspect();
	}

	public void writeDiagramScales() {
		out.onNewLine().add("var scale_x = d3.scaleLinear(), scale_y = d3.scaleLinear()").endStatement();
		writeScaleExtras();
	}

	private void writeAspect() {
		Double aspect = getAspect();
		boolean anyCategorial = structure.coordinates.xCategorical || structure.coordinates.yCategorical;

		if (aspect != null && !anyCategorial) {
			out.onNewLine().add("BrunelD3.setAspect(scale_x, scale_y, " + aspect + ")");
			out.endStatement();
		}
	}

	private void writePositionScale(ScalePurpose purpose, Field[] fields, String range, boolean fillToEdge, boolean reverse) {
		int categories = defineScaleWithDomain(purpose.name(), fields, purpose, 2, "linear", null, reverse);
		out.addChained("range(" + range + ")");
		if (categories > 0 && fillToEdge)
			out.add(".padding(0)");
		out.endStatement();
	}

	private String getXRange() {
		if (structure.coordinates.isPolar()) return "[0, geom.inner_radius]";
		if (structure.coordinates.isTransposed()) return "[geom.inner_width, 0]";
		return "[0, geom.inner_width]";
	}

	private String getYRange() {
		if (structure.coordinates.isPolar()) return "[0, Math.PI*2]";
		if (structure.coordinates.isTransposed()) return "[0, geom.inner_height]";
		return "[geom.inner_height, 0]";
	}

	private Double getAspect() {
		//Find Param with "aspect" and return its value
		for (VisSingle e : elements) {
			for (Param p : e.fCoords) {
				if (p.asString().equals("aspect")) {
					Param m = p.firstModifier();
					//Use "square" for 1.0 aspect ratio
					if (m.asString().equals("square")) return 1.0;
					else return m.asDouble();
				}
			}
		}
		return null;
	}

	/**
	 * Defines a scale,a dding the domain info but not the range
	 *
	 * @param name              if not null, define a new variable for the scale, otehrwise assume it has been done
	 * @param fields            one or more fields for this scale
	 * @param purpose           the purpose influences how we make the scale
	 * @param numericDomainDivs desired number of numeric ticks
	 * @param defaultTransform  linear, log, root
	 * @param partitionPoints   if defined, use these explicit partitions
	 * @param reverse           do we want to reverse the scale
	 * @return number of categories in scale, or -1 if not categorical
	 */
	public int defineScaleWithDomain(String name, Field[] fields, ScalePurpose purpose, int numericDomainDivs,
									 String defaultTransform, Object[] partitionPoints, boolean reverse) {
		boolean isX = purpose == ScalePurpose.x, isY = purpose == ScalePurpose.y;

		if (name != null)
			out.onNewLine().add("var scale_" + name, "= ");

		// No position for this dimension, so util a default [0,1] scale
		if (fields.length == 0) return makeEmptyZeroOneScale();

		// Categorical field (includes binned data)
		if (ModelUtil.combinationIsCategorical(fields, purpose.isCoord))
			return makeCategoricalScale(fields, purpose, reverse);

		Field field = fields[0];

		// Determine how much we want to include zero (for size scale we always want it)
		double includeZero = getIncludeZeroFraction(fields, purpose);
		if (purpose == ScalePurpose.size) includeZero = 1.0;

		// Build a combined scale field and force the desired transform on it for x and y dimensions
		Field scaleField = fields.length == 1 ? field : combineNumericFields(fields);
		ChartCoordinates coordinates = structure.coordinates;
		if (isX) {
			// We need to copy it as we are modifying it
			if (scaleField == field) scaleField = field.rename(field.name, field.label);
			scaleField.set("transform", coordinates.xTransform);
		} else if (isY) {
			// We need to copy it as we are modifying it
			if (scaleField == field) scaleField = field.rename(field.name, field.label);
			scaleField.set("transform", coordinates.yTransform);
		}

		// We util a nice scale only for rectangular coordinates
		boolean nice = purpose.isCoord && coords != Coordinates.polar;
		double[] padding = getNumericPaddingFraction(purpose, coords);

		// Areas and line should fill the horizontal dimension, as should any binned field
		if (scaleField.isBinned() || isX && elementsFillHorizontal(ScalePurpose.x)) {
			nice = false;
			padding = new double[]{0, 0};
			includeZero = 0;
		}

		NumericScale detail = Auto.makeNumericScale(scaleField, nice, padding, includeZero, 9, false);
		double min = detail.min;
		double max = detail.max;

		Double[] extent = isX ? coordinates.xExtent : coordinates.yExtent;
		if (extent != null && extent[0] != null) min = extent[0];
		if (extent != null && extent[1] != null) max = extent[1];

		Object[] divs = new Object[numericDomainDivs];
		if (field.isDate()) {
			DateFormat dateFormat = (DateFormat) field.property("dateFormat");
			if (isX) dateFormat = coordinates.xDateFormat;
			if (isY) dateFormat = coordinates.yDateFormat;

			DateBuilder dateBuilder = new DateBuilder();
			for (int i = 0; i < divs.length; i++) {
				Object v;
				if (partitionPoints == null)
					v = min + (max - min) * i / (numericDomainDivs - 1);
				else
					v = partitionPoints[i];
				divs[i] = dateBuilder.make(Data.asDate(v), dateFormat, true);
			}
			out.add("d3.scaleTime()");
		} else {
			// If requested to have a specific transform, util that. Otherwise util the one the field suggests.
			// Some scales (like for an area size) have a default transform (e.g. root) and we
			// util that if the field wants a linear scale.
			String transform = null;
			if (isX) transform = coordinates.xTransform;
			if (isY) transform = coordinates.yTransform;

			// Size must not get a transform as it will seriously distort things
			if (purpose == ScalePurpose.size) transform = defaultTransform;

			// Parallel axes get their transform defined
			if (purpose == ScalePurpose.parallel) {
				if (defaultTransform == null) {
					defaultTransform = "linear";
				} else {
					transform = defaultTransform;
				}
			}

			transform = makeD3ScaleName(defaultTransform, scaleField, transform);

			// Add small amount to top end to avoid ticks missing due to round-off error
			max += (max - min) * 1e-7;

			out.add("d3." + transform + "()");
			for (int i = 0; i < divs.length; i++) {
				if (partitionPoints == null)
					divs[i] = min + (max - min) * i / (numericDomainDivs - 1);
				else
					divs[i] = partitionPoints[i];
			}
		}

		if (reverse) {
			List<Object> l = Arrays.asList(divs);
			Collections.reverse(l);
			divs = l.toArray();
		}

		String domain = Data.join(divs);
		out.add(".domain([").add(domain).add("])");
		return -1;
	}

	private int makeCategoricalScale(Field[] fields, ScalePurpose purpose, boolean reverse) {
		// Combine all categories in the position after each color
		// We use all the categories in the data; we do not need the partition points
		List<Object> list = getCategories(fields);
		if (reverse) Collections.reverse(list);
		if (purpose == ScalePurpose.parallel)
			out.add("d3.scalePoint()");
		else
			out.add(purpose.isCoord ? "d3.scalePoint().padding(0.5)" : "d3.scaleOrdinal()");
		out.addChained("domain([");
		// Write numbers as numbers, everything else becomes a string
		for (int i = 0; i < list.size(); i++) {
			Object o = list.get(i);
			if (i > 0) out.add(", ");
			if (o instanceof Number) out.add(Data.format(o, false));
			else out.add(Data.quote(o.toString()));
		}
		out.add("])");
		return list.size();
	}

	private int makeEmptyZeroOneScale() {
		out.add("d3.scaleLinear().domain([0,1])");
		return -1;
	}

	// Create the D3 scale name
	private String makeD3ScaleName(String defaultTransform, Field scaleField, String transform) {
		if (transform == null) {
			// We are free to choose -- the user did not specify
			transform = (String) scaleField.property("transform");
			if (transform == null) transform = "linear";
			if (transform.equals("linear")) transform = defaultTransform;
		}
		if (transform.equals("root") || transform.equals("sqrt")) return "scaleSqrt";
		if (transform.equals("log")) return "scaleLog";
		if (transform.equals("linear")) return "scaleLinear";
		throw new IllegalStateException("Unknown scale type: " + transform);
	}

	public List<Object> getCategories(Field[] ff) {
		Set<Object> all = new LinkedHashSet<>();
		for (Field f : ff) if (f.preferCategorical()) Collections.addAll(all, f.categories());
		return new ArrayList<>(all);
	}

	private double getIncludeZeroFraction(Field[] fields, ScalePurpose purpose) {

		if (purpose == ScalePurpose.x) return 0.1;               // Really do not want much empty space on color axes
		if (purpose == ScalePurpose.size) return 0.98;           // Almost always want to go to zero
		if (purpose == color) return 0.2;           // Color

		// For 'Y'

		// If any position are  counts or sums, always include zero
		for (Field f : fields)
			if (f.name.equals("#count") || "sum".equals(f.strProperty("summary"))) return 1.0;

		int nBarArea = 0;       // Count elements that are bars or areas
		for (VisSingle e : elements)
			if ((e.tElement == Element.bar || e.tElement == Element.area)
					&& e.fRange == null) nBarArea++;

		if (nBarArea == elements.length) return 1.0;        // All bars?  Always go to zero
		if (nBarArea > 0) return 0.8;                       // Some bars?  Strongly want to go to zero
		return 0.2;                                         // By default, only if we have 20% extra space
	}

	private Field combineNumericFields(Field[] ff) {
		List<Object> data = new ArrayList<>();
		for (Field f : ff)
			for (int i = 0; i < f.rowCount(); i++) {
				Object value = f.value(i);
				if (value instanceof Range) {
					data.add(Data.asNumeric(((Range) value).low));
					data.add(Data.asNumeric(((Range) value).high));
				} else
					data.add(Data.asNumeric(value));
			}
		Field combined = Fields.makeColumnField("combined", null, data.toArray(new Object[data.size()]));
		combined.set("numeric", true);
		return combined;
	}

	private double[] getNumericPaddingFraction(ScalePurpose purpose, Coordinates coords) {
		double[] padding = new double[]{0, 0};
		if (purpose == color || purpose == ScalePurpose.size)
			return padding;                // None for aesthetics
		if (coords == Coordinates.polar) return padding;                               // None for polar angle
		for (VisSingle e : elements) {
			boolean noBottomYPadding = e.tElement == Element.bar || e.tElement == Element.area || e.tElement == Element.line;
			if (e.tElement == Element.text) {
				// Text needs lot of padding
				padding[0] = Math.max(padding[0], 0.1);
				padding[1] = Math.max(padding[1], 0.1);
			} else if (purpose == ScalePurpose.y && noBottomYPadding) {
				// A little padding on the top only
				padding[1] = Math.max(padding[1], 0.02);
			} else {
				// A little padding
				padding[0] = Math.max(padding[0], 0.02);
				padding[1] = Math.max(padding[1], 0.02);
			}
		}
		return padding;
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

				DateBuilder dateBuilder = new DateBuilder();
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

	private void addColorScale(Param p, VisSingle vis) {
		Field f = fieldById(p, vis);

		// Determine if the element fills a big area
		boolean largeElement = vis.tElement == Element.area || vis.tElement == Element.bar
				|| vis.tElement == Element.polygon;
		if (vis.tDiagram == Diagram.map || vis.tDiagram == Diagram.treemap)
			largeElement = true;

		if (vis.tElement == Element.path && !vis.fSize.isEmpty())
			largeElement = true;

		ColorMapping palette = Palette.makeColorMapping(f, p.modifiers(), largeElement);
		int categories = defineScaleWithDomain("color", new Field[]{f}, color, palette.values.length, "linear", palette.values, false);
		if (categories <= 0) out.addChained("interpolate(d3.interpolateHcl)");   // Interpolate for numeric only
		out.addChained("range([ ").addQuoted((Object[]) palette.colors).add("])").endStatement();
	}

	private void addSymbolScale(Param p, ElementStructure element) {
		Field f = fieldById(p, element.vis);                                // Find the field
		SymbolHandler symbols = structure.symbols;                          // Handler for all symbols
		String[] names = symbols.getNamesForElement(element);            	// List of symbol identifiers

		defineScaleWithDomain("symbol", new Field[]{f}, color, names.length, "linear", null, false);
		out.addChained("range([ ").addQuoted((Object[]) names).add("])").endStatement();
	}

	private void addOpacityScale(Param p, VisSingle vis) {
		double min = p.hasModifiers() ? p.firstModifier().asDouble() : 0.2;
		Field f = fieldById(p, vis);

		defineScaleWithDomain("opacity", new Field[]{f}, color, 2, "linear", null, false);
		if (f.preferCategorical()) {
			int length = f.categories().length;
			double[] sizes = new double[length];
			// degenerate data gets the min value
			if (length == 1)
				sizes[0] = min;
			else
				for (int i = 0; i < length; i++) sizes[i] = min + (1 - min) * i / (length - 1);
			out.addChained("range(" + Arrays.toString(sizes) + ")");
		} else {
			out.addChained("range([" + min + ", 1])");
		}
		out.endStatement();
	}

	private void addSizeScale(String name, Param p, VisSingle vis, String defaultTransform) {

		Object[] sizes;
		if (p.hasModifiers()) {
			sizes = getSizes(p.modifiers()[0].asList());
		} else {
			sizes = new Object[]{MIN_SIZE_FACTOR, 1.0};
		}

		Field f = fieldById(p, vis);
		Object[] divisions = f.isNumeric() ? null : f.categories();
		defineScaleWithDomain(name, new Field[]{f}, ScalePurpose.size, sizes.length, defaultTransform, divisions, false);
		out.addChained("range([ ").add(Data.join(sizes)).add("])").endStatement();
	}

	private Field fieldById(Param p, VisSingle vis) {
		return fieldById(p.asField(), vis);
	}

	private Param getOpacity(VisSingle vis) {
		return vis.fOpacity.isEmpty() ? null : vis.fOpacity.get(0);
	}

	/**
	 * Returns position for the sizes as an array
	 */
	private Param[] getSize(VisSingle vis) {
		List<Param> fSize = vis.fSize;
		return fSize.toArray(new Param[fSize.size()]);
	}

	/**
	 * Returns css class aesthetics as an array
	 */
	private Param[] getCSSAesthetics(VisSingle vis) {
		return vis.fCSS.toArray(new Param[vis.fCSS.size()]);
	}

	/**
	 * Returns css class aesthetics as an array
	 */
	private Param[] getSymbolAesthetics(VisSingle vis) {
		return vis.fSymbol.toArray(new Param[vis.fSymbol.size()]);
	}

	private Object[] getSizes(List<Param> params) {
		// The parameters define the lists we want
		List<Double> result = new ArrayList<>();
		for (Param p : params) {
			String s = p.asString();
			if (s.endsWith("%")) s = s.substring(0, s.length() - 1);
			Double d = Data.asNumeric(s);
			if (d != null) result.add(d / 100);
		}
		if (result.isEmpty()) return new Object[]{MIN_SIZE_FACTOR, 1.0};
		if (result.size() == 1) result.add(0, MIN_SIZE_FACTOR);
		return result.toArray(new Object[result.size()]);
	}

	private static final class AxisSpec {
		static final AxisSpec DEFAULT = new AxisSpec();

		final int ticks;
		final String name;
		final boolean grid;
		final boolean reverse;

		private AxisSpec() {
			ticks = 9999;
			name = null;
			grid = false;
			reverse = false;
		}

		public AxisSpec(int ticks, String name, boolean grid, boolean reverse) {
			this.ticks = ticks;
			this.name = name;
			this.grid = grid;
			this.reverse = reverse;
		}

		public AxisSpec merge(Param[] params) {
			AxisSpec result = this;
			for (Param p : params) {
				if (p.type() == Type.number) {
					int newTicks = Math.min((int) p.asDouble(), result.ticks);
					result = new AxisSpec(newTicks, result.name, result.grid, result.reverse);
				} else if (p.type() == Type.string) {
					String newTitle = p.asString();
					result = new AxisSpec(result.ticks, newTitle, result.grid, result.reverse);
				} else if (p.type() == Type.option) {
					if ("grid".equals(p.asString()))
						result = new AxisSpec(result.ticks, result.name, true, result.reverse);
					else if ("reverse".equals(p.asString()))
						result = new AxisSpec(result.ticks, result.name, result.grid, true);
				}
			}
			return result;
		}
	}

}
