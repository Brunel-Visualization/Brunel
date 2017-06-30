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

package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.info.ChartCoordinates;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuildUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.color.ColorMapping;
import org.brunel.color.Palette;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.auto.Auto;
import org.brunel.data.auto.Domain;
import org.brunel.data.auto.DomainSpan;
import org.brunel.data.auto.NumericExtentDetail;
import org.brunel.data.auto.NumericScale;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;
import org.brunel.model.VisElement;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
public class ScaleBuilder {

	private static final double MIN_SIZE_FACTOR = 0.001;

	private final ChartStructure structure;         // Overall detail on the chart composition
	private final VisElement[] elements;             // The elements that define the scales used
	private final ScriptWriter out;                 // Write definitions to here

	public ScaleBuilder(ChartStructure structure, ScriptWriter out) {
		this.structure = structure;
		this.out = out;
		this.elements = structure.elements;

	}

	public boolean allNumeric(Field[] fields) {
		for (Field f : fields)
			if (!f.isNumeric())
				return false;
		return true;
	}

	/**
	 * Defines a scale, adding the domain info but not the range
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
		// Scales are generally named by their type ("x", "color", etc.) and a suffix "_scale"
		if (name != null) out.onNewLine().add("var scale_" + name, "= ");

		// Build a domain for the fields. If we have a mix of fields that can support either continuous or
		// categorical entries (e.g. binned data), let the purpose define the preference
		Domain domain = new Domain(purpose.preferContinuous());
		for (Field field : fields) domain.include(field);

		// Easy case -- no domains at all
		if (domain.spanCount() == 0) return makeEmptyZeroOneScale();

		/*
		 * TODO: Handle mixed domains
		 * The domain is built up and understands mixed numeric and categorical domains, but
		 * in the following code we ignore that and just use the first in the list
		 */
		DomainSpan span = domain.span(0);

		// Categorical is relatively easy
		if (!domain.span(0).isNumeric())
			return makeCategoricalScale(span.content(), purpose, reverse);

		// Determine how much we want to include zero
		double includeZero = getIncludeZeroFraction(purpose, domain.span(0).desiresZero());

		// Build a combined scale field and force the desired transform on it for x and y dimensions
		Field field = fields[0];
		Field scaleField = fields.length == 1 ? field : combineNumericFields(fields);
		ChartCoordinates coordinates = structure.coordinates;
		if (purpose == ScalePurpose.x) {
			// We need to copy it as we are modifying it
			if (scaleField == field) scaleField = field.rename(field.name, field.label);
			scaleField.set("transform", coordinates.xTransform);
		} else if (purpose == ScalePurpose.y) {
			// We need to copy it as we are modifying it
			if (scaleField == field) scaleField = field.rename(field.name, field.label);
			scaleField.set("transform", coordinates.yTransform);
		}

		// We util a nice scale only for rectangular coordinates
		boolean nice = purpose.isCoord && !structure.coordinates.isPolar();
		double[] padding = getNumericPaddingFraction(purpose);

		// Areas and line should fill the horizontal dimension, as should any binned field
		if (scaleField.isBinned() || purpose == ScalePurpose.x && elementsFillHorizontal(ScalePurpose.x)) {
			nice = false;
			padding = new double[]{0, 0};
			includeZero = 0;
		}

		NumericScale detail = Auto.makeNumericScale(NumericExtentDetail.makeForField(scaleField), nice, padding, includeZero, 9, false);
		double min = detail.min;
		double max = detail.max;

		Double[] extent = purpose == ScalePurpose.x ? coordinates.xExtent : coordinates.yExtent;
		if (extent != null && extent[0] != null) min = extent[0];
		if (extent != null && extent[1] != null) max = extent[1];

		Object[] divs = new Object[numericDomainDivs];
		if (field.isDate()) {
			DateFormat dateFormat = (DateFormat) field.property("dateFormat");
			if (purpose == ScalePurpose.x) dateFormat = coordinates.xDateFormat;
			if (purpose == ScalePurpose.y) dateFormat = coordinates.yDateFormat;

			BuildUtil.DateBuilder dateBuilder = new BuildUtil.DateBuilder();
			for (int i = 0; i < divs.length; i++) {
				Object v;
				if (partitionPoints == null)
					v = min + (max - min) * i / (numericDomainDivs - 1);
				else
					v = partitionPoints[i];
				divs[i] = dateBuilder.make(Data.asDate(v), dateFormat, true);
			}
			out.add("d3.scaleUtc()");
		} else {
			// If requested to have a specific transform, util that. Otherwise util the one the field suggests.
			// Some scales (like for an area size) have a default transform (e.g. root) and we
			// util that if the field wants a linear scale.
			String transform = null;
			if (purpose == ScalePurpose.x) transform = coordinates.xTransform;
			if (purpose == ScalePurpose.y) transform = coordinates.yTransform;

			// Size must not get a transform as it will seriously distort things
			if (purpose == ScalePurpose.sizeAesthetic) transform = defaultTransform;

			// Parallel axes get their transform defined
			if (purpose == ScalePurpose.parallel) {
				if (defaultTransform == null) {
					defaultTransform = "linear";
				} else {
					transform = defaultTransform;
				}
			}

			transform = makeD3ScaleName(defaultTransform, scaleField, transform);

			max += (max - min) * 1e-7;

			// Adjust scale when we know we need quantization
			if (purpose.isNominal && transform.equals("scaleLinear")) {
				out.add("d3.scaleQuantize()");                            // Use quantize scale
				divs = new Object[]{min, max};                            // And just the min and max
			} else {
				// Add small amount to top end to avoid ticks missing due to round-off error
				for (int i = 0; i < divs.length; i++) {
					if (partitionPoints == null)
						divs[i] = min + (max - min) * i / (numericDomainDivs - 1);
					else
						divs[i] = partitionPoints[i];
				}
				out.add("d3." + transform + "()");
			}

		}

		if (reverse) {
			List<Object> l = Arrays.asList(divs);
			Collections.reverse(l);
			divs = l.toArray();
		}

		String domainDivs = Data.join(divs);
		out.add(".domain([").add(domainDivs).add("])");
		return -1;
	}

	public boolean elementsFillHorizontal(ScalePurpose purpose) {
		for (VisElement e : elements) {
			// All must be lines or areas to fill to the edge
			if (e.tElement != Element.line && e.tElement != Element.area) return false;
			// There must be no clustering on the X axis
			if (purpose == ScalePurpose.x && e.fX.size() > 1) return false;
		}

		return true;
	}

	public List<Object> getCategories(Field[] ff) {
		Set<Object> all = new LinkedHashSet<>();
		for (Field f : ff) if (f.preferCategorical()) Collections.addAll(all, f.categories());
		return new ArrayList<>(all);
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

	public void writeAestheticScales(ElementStructure structure) {
		VisElement vis = structure.vis;

		// Some node structures have the data within a 'data' fields instead of at the top level
		boolean dataInside = structure.hasHierarchicalData() && !structure.isDependent();

		Param color = getColor(vis);
		Param symbol = getSymbol(vis);
		Param[] size = getSize(vis);
		Param[] css = getCSSAesthetics(vis);
		Param opacity = getOpacity(vis);
		if (color == null && opacity == null && size.length == 0 && css.length == 0 && symbol == null) return;

		out.onNewLine().comment("Aesthetic Functions");
		if (color != null) {
			addColorScale(color, vis);
			Field field = fieldById(color, vis);
			out.onNewLine().add("var color = function(d) { var c = " + BuildUtil.writeCall(field, dataInside)
					+ "; return c!=null ? scale_color(c) : null }").endStatement();
		}
		if (opacity != null) {
			addOpacityScale(opacity, vis);
			Field field = fieldById(opacity, vis);
			out.onNewLine().add("var opacity = function(d) { var c = " + BuildUtil.writeCall(field, dataInside)
					+ "; return c!=null ? scale_opacity(c) : null }").endStatement();
		}
		if (symbol != null) {
			addSymbolScale(symbol, structure);
			Field field = fieldById(symbol, vis);
			out.onNewLine().add("var symbolID = function(d) { var s =" + BuildUtil.writeCall(field, dataInside)
					+ "; return s!=null ? scale_symbol(s) : '_sym_circle' }").endStatement();
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

			// Trees built without edges have their sizes calculated as the "value"
			String value = vis.tDiagram == Diagram.tree && !structure.isSourceForDependent()
					? "d.value" : BuildUtil.writeCall(field, dataInside);

			out.onNewLine().add("var size = function(d) { var s = " + value + "; return s!=null ? scale_size(s) : null }").endStatement();
		} else if (size.length > 1) {
			// We have two field and util them for height and width
			addSizeScale("width", size[0], vis, "linear");
			addSizeScale("height", size[1], vis, "linear");
			Field widthField = fieldById(size[0], vis);
			out.onNewLine().add("var width = function(d) { return scale_width(" + BuildUtil.writeCall(widthField, dataInside) + ") }").endStatement();
			Field heightField = fieldById(size[1], vis);
			out.onNewLine().add("var height = function(d) { return scale_height(" + BuildUtil.writeCall(heightField, dataInside) + ") }").endStatement();
		}
	}

	public void writeCoordinateScales() {
		ChartCoordinates coordinates = structure.coordinates;
		writePositionScale(ScalePurpose.x, coordinates.allXFields, getXRange(), elementsFillHorizontal(ScalePurpose.x), coordinates.xReversed);
		writePositionScale(ScalePurpose.inner, coordinates.allXClusterFields, "[-0.5, 0.5]", elementsFillHorizontal(ScalePurpose.inner), coordinates.xReversed);
		writePositionScale(ScalePurpose.y, coordinates.allYFields, getYRange(), false, coordinates.yReversed);
		writeScaleExtras();
	}

	public void writeDiagramScales() {
		out.onNewLine().add("var scale_x = d3.scaleLinear(), scale_y = d3.scaleLinear()").endStatement();
		writeScaleExtras();
	}

	private void addColorScale(Param p, VisElement vis) {
		Field f = fieldById(p, vis);

		// Determine if the element fills a big area
		boolean largeElement = vis.tElement == Element.area || vis.tElement == Element.bar
				|| vis.tElement == Element.polygon;
		if (vis.tDiagram == Diagram.map || vis.tDiagram == Diagram.treemap)
			largeElement = true;

		if (vis.tElement == Element.path && !vis.fSize.isEmpty())
			largeElement = true;

		ColorMapping palette = Palette.makeColorMapping(f, p.modifiers(), largeElement);
		int categories = defineScaleWithDomain("color", new Field[]{f}, ScalePurpose.continuousAesthetic, palette.values.length, "linear", palette.values, false);
		if (categories <= 0) out.addChained("interpolate(d3.interpolateHcl)");   // Interpolate for numeric only
		out.addChained("range([ ").addQuoted((Object[]) palette.colors).add("])").endStatement();
	}

	private void addOpacityScale(Param p, VisElement vis) {
		double min = p.hasModifiers() ? p.firstModifier().asDouble() : 0.2;
		Field f = fieldById(p, vis);

		defineScaleWithDomain("opacity", new Field[]{f}, ScalePurpose.continuousAesthetic, 2, "linear", null, false);
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

	private void addSizeScale(String name, Param p, VisElement vis, String defaultTransform) {

		Object[] sizes;
		if (p.hasModifiers()) {
			sizes = getSizes(p.modifiers()[0].asList());
		} else {
			sizes = new Object[]{MIN_SIZE_FACTOR, 1.0};
		}

		Field f = fieldById(p, vis);
		Object[] divisions = f.isNumeric() ? null : f.categories();
		defineScaleWithDomain(name, new Field[]{f}, ScalePurpose.sizeAesthetic, sizes.length, defaultTransform, divisions, false);
		out.addChained("range([ ").add(Data.join(sizes)).add("])").endStatement();
	}

	private void addSymbolScale(Param p, ElementStructure element) {
		Field f = fieldById(p, element.vis);                                    // Find the field
		SymbolHandler symbols = structure.symbols;                              // Handler for all symbols
		String[] requestedSymbols = symbols.findRequiredSymbolNames(p);            // Lists of symbols requested
		String[] symbolIDs = symbols.getSymbolIDs(element, requestedSymbols);   // List of symbol identifiers

		defineScaleWithDomain("symbol", new Field[]{f}, ScalePurpose.nominalAesthetic, symbolIDs.length, "linear", null, false);
		out.addChained("range([ ").addQuoted((Object[]) symbolIDs).add("])").endStatement();
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

	private void defineCSSClassAesthetic(VisElement vis, boolean dataInside, Param p, String suffix) {
		Field field = fieldById(p, vis);

		// Define the prefix for the classes we will use
		String cssPrefix = p.hasModifiers() ? p.firstModifier().asString() : "brunel_class_";

		// Only use names if specifically requested
		boolean useNames = p.hasModifierOption("names");

		if (useNames) {
			// This is easy as we have no need for a scale -- we just use the raw values coming from the field
			out.onNewLine().add("var css" + suffix + " = function(d) { return " + Data.quote(cssPrefix) + " + "
					+ BuildUtil.writeCall(field, dataInside) + " }").endStatement();
			return;
		}

		// Define the scale
		out.add("var scale_css" + suffix + " = ");

		if (field.preferCategorical()) {
			// Each category is mapped to 1,2,3, etc.
			int categories = makeCategoricalScale(field.categories(), ScalePurpose.nominalAesthetic, false);
			String[] indices = new String[categories];
			for (int i = 0; i < indices.length; i++) indices[i] = Data.quote(cssPrefix + (i + 1));
			out.addChained("range(" + Arrays.toString(indices) + ")");
		} else {
			// Quantize the scale
			List<? extends Object> divisions = p.firstListModifier();
			if (divisions == null)
				divisions = Arrays.asList(field.min(), (Object) field.max());

			out.add("d3.scaleQuantize().domain([" + Data.join(divisions) + "]).range(["
					+ Data.quote(cssPrefix + "1") + ", " + Data.quote(cssPrefix + "2") + "])");
		}

		out.endStatement()
				.add("var css" + suffix + " = function(d) { return scale_css" + suffix + "("
						+ BuildUtil.writeCall(field, dataInside) + ") }").endStatement();
	}

	private Field fieldById(String fieldName, VisElement vis) {
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] == vis) {
				Field field = structure.elementStructure[i].data.field(fieldName);
				if (field == null) throw new IllegalStateException("Unknown field " + fieldName);
				return field;
			}
		}
		throw new IllegalStateException("Passed in a vis that was not part of the system defined in the constructor");
	}

	private Field fieldById(Param p, VisElement vis) {
		return fieldById(p.asField(), vis);
	}

	private Double getAspect() {
		//Find Param with "aspect" and return its value
		for (VisElement e : elements) {
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
	 * Returns css class aesthetics as an array
	 */
	private Param[] getCSSAesthetics(VisElement vis) {
		return vis.fCSS.toArray(new Param[vis.fCSS.size()]);
	}

	private Param getColor(VisElement vis) {
		return vis.fColor.isEmpty() ? null : vis.fColor.get(0);
	}

	/**
	 * This returns the fraction of the scale we are happy to be empty to ensure zero is on the scale
	 *
	 * @param purpose                 scale purpose
	 * @param domainWantsZeroIncluded if the domain knows it wants it
	 * @return the percentage white space we are willing to tolerate
	 */
	private double getIncludeZeroFraction(ScalePurpose purpose, boolean domainWantsZeroIncluded) {

		if (purpose == ScalePurpose.sizeAesthetic) return 1.0;        // Size always goes to zero
		if (purpose == ScalePurpose.x) return 0.1;                    // Do not want much empty space on x axis
		if (domainWantsZeroIncluded) return 1.0;

		// Count number of bar elements that stretch to the lower axis
		if (purpose.isCoord) {
			int nBarArea = 0;
			for (VisElement e : elements)
				if ((e.tElement == Element.bar || e.tElement == Element.area)
						&& e.fRange == null) nBarArea++;

			if (nBarArea == elements.length) return 1.0;    // All bars?  Always go to zero
			if (nBarArea > 0) return 0.8;                   // Some bars?  Strongly want to go to zero
		}

		return 0.2;                                         // 20% is the default
	}

	private double[] getNumericPaddingFraction(ScalePurpose purpose) {
		double[] padding = new double[]{0, 0};
		if (!purpose.isCoord) return padding;                    // None for aesthetics
		if (structure.coordinates.isPolar()) return padding;    // None for polar angle
		for (VisElement e : elements) {
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

	private Param getOpacity(VisElement vis) {
		return vis.fOpacity.isEmpty() ? null : vis.fOpacity.get(0);
	}

	/**
	 * Returns position for the sizes as an array
	 */
	private Param[] getSize(VisElement vis) {
		List<Param> fSize = vis.fSize;
		return fSize.toArray(new Param[fSize.size()]);
	}

	public static Double[] getSizes(List<Param> params) {
		// The parameters define the lists we want
		List<Double> result = new ArrayList<>();
		for (Param p : params) {
			String s = p.asString();
			if (s.endsWith("%")) s = s.substring(0, s.length() - 1);
			Double d = Data.asNumeric(s);
			if (d != null) result.add(d / 100);
		}
		if (result.isEmpty()) return new Double[]{MIN_SIZE_FACTOR, 1.0};
		if (result.size() == 1) result.add(0, MIN_SIZE_FACTOR);
		return result.toArray(new Double[result.size()]);
	}

	private Param getSymbol(VisElement vis) {
		return vis.fSymbol.isEmpty() ? null : vis.fSymbol.get(0);
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

	private int makeCategoricalScale(Object[] items, ScalePurpose purpose, boolean reverse) {
		if (purpose == ScalePurpose.parallel)
			out.add("d3.scalePoint()");
		else
			out.add(purpose.isCoord ? "d3.scalePoint().padding(0.5)" : "d3.scaleOrdinal()");
		out.addChained("domain([");

		int n = items.length;
		for (int i = 0; i < n; i++) {
			Object o = items[reverse ? n - 1 - i : i];
			if (i > 0) out.add(", ");
			if (o instanceof Number) out.add(Data.format(o, false));
			else out.add(Data.quote(o.toString()));
		}
		out.add("])");

		return items.length;
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

	private int makeEmptyZeroOneScale() {
		out.add("d3.scaleLinear().domain([0,1])");
		return -1;
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

	private void writeScaleExtras() {
		out.onNewLine().add("var base_scales = [scale_x, scale_y];").comment("Untransformed original scales");
		writeAspect();
	}

}
