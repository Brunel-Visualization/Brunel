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

package org.brunel.build.info;

import org.brunel.action.Param;
import org.brunel.build.data.TransformedData;
import org.brunel.build.util.ModelUtil;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.util.DateFormat;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keep information on fields used for position within a visualization
 */
public class ChartCoordinates {

	public final VisTypes.Coordinates coords;
	public final Field[] allXFields, allYFields, allXClusterFields;
	public final String xTransform, yTransform;
	public final boolean xCategorical, yCategorical;                // Basic measure for the x and y dimensions
	public final DateFormat xDateFormat, yDateFormat;              	// If not null, the dimension is a date
	public final Double[] xExtent, yExtent;                         // User-provided data overrides for the extents
	public final boolean xReversed, yReversed;

	private final Map<VisSingle, Field[]> x = new HashMap<>();
	private final Map<VisSingle, Field[]> y = new HashMap<>();

	public ChartCoordinates(VisSingle[] elements, TransformedData[] data, VisTypes.Diagram diagram) {

		this.coords = makeCombinedCoords(elements, diagram);

		String xTransform = null, yTransform = null;                // If defined by the VisSingle

		double xMin = Double.POSITIVE_INFINITY, yMin = Double.POSITIVE_INFINITY;
		double xMax = Double.NEGATIVE_INFINITY, yMax = Double.NEGATIVE_INFINITY;

		ArrayList<Field> allX = new ArrayList<>();
		ArrayList<Field> allY = new ArrayList<>();
		ArrayList<Field> allCluster = new ArrayList<>();
		for (int i = 0; i < elements.length; i++) {
			VisSingle vis = elements[i];
			if (vis.tDiagram == null) {
				Field[] visXFields = getXFields(vis, data[i]);
				Field[] visYFields = getYFields(vis, data[i]);

				if (xTransform == null) xTransform = getDefinedXTransform(vis);
				if (yTransform == null) yTransform = getDefinedYTransform(vis);

				x.put(vis, visXFields);
				y.put(vis, visYFields);

				xMin = Math.min(xMin, getDefinedExtent(vis.fX, true));
				yMin = Math.min(yMin, getDefinedExtent(vis.fY, true));
				xMax = Math.max(xMax, getDefinedExtent(vis.fX, false));
				yMax = Math.max(yMax, getDefinedExtent(vis.fY, false));

				if (visXFields.length > 0)
					allX.add(visXFields[0]);             // Only first X field (rest are clustered)
				if (visXFields.length > 1) allCluster.add(visXFields[1]);       // Add the clustered X field
				Collections.addAll(allY, visYFields);                           // All Y fields (used in ranges)
			} else {
				x.put(vis, new Field[0]);
				y.put(vis, new Field[0]);
			}
		}

		if (Double.isInfinite(xMin) && Double.isInfinite(xMax))
			xExtent = null;
		else
			xExtent = new Double[]{Double.isInfinite(xMin) ? null : xMin, Double.isInfinite(xMax) ? null : xMax};
		if (Double.isInfinite(yMin) && Double.isInfinite(yMax))
			yExtent = null;
		else
			yExtent = new Double[]{Double.isInfinite(yMin) ? null : yMin, Double.isInfinite(yMax) ? null : yMax};

		this.allXFields = allX.toArray(new Field[allX.size()]);
		this.allYFields = allY.toArray(new Field[allY.size()]);
		this.allXClusterFields = allCluster.toArray(new Field[allCluster.size()]);

		// Set ordinal / categorical and derive transforms (if not explicitly set above)
		this.xCategorical = ModelUtil.combinationIsCategorical(allXFields, true);
		this.yCategorical = ModelUtil.combinationIsCategorical(allYFields, true);

		if (xTransform == null)
			this.xTransform = xCategorical ? "linear" : chooseTransform(allXFields);
		else
			this.xTransform = xTransform;

		if (yTransform == null)
			this.yTransform = yCategorical ? "linear" : chooseTransform(allYFields);
		else
			this.yTransform = yTransform;

		this.xDateFormat = makeDateFormat(allXFields);
		this.yDateFormat = makeDateFormat(allYFields);

		// Basic assumptions for reversing -- Don't on the horizontal, do on the vertical if categorical
		// Ensures numeric on Y reads bottom-up, while categorical reads top-down
		boolean reverseX = isTransposed() && xCategorical;
		boolean reverseY = !isTransposed() && yCategorical;

		if (needsReverse(elements, true)) reverseX = !reverseX;
		if (needsReverse(elements, false)) reverseY = !reverseY;

		xReversed = reverseX;
		yReversed = reverseY;
	}

	private DateFormat makeDateFormat(Field[] fields) {
		// Run through the fields and return the largest date format applicable
		// (so month rather than day) or null if no fields had date format
		DateFormat result = null;
		for (Field field : fields) {
			DateFormat f = (DateFormat) field.property("dateFormat");
			if (result == null || f.compareTo(result) > 0) result = f;
		}
		return result;
	}

	private boolean needsReverse(VisSingle[] elements, boolean forX) {
		for (VisSingle element : elements) {
			// Which params to look through -- X or Y?
			List<Param> pp = forX ? element.fX : (element.fRange == null ? element.fY : Arrays.asList(element.fRange));

			// Look for 'reverse'
			for (Param p : pp) if (p.hasModifierOption("reverse")) return true;
		}

		return false;

	}

	public boolean isPolar() {
		return coords == VisTypes.Coordinates.polar;
	}

	public boolean isTransposed() {
		return coords == VisTypes.Coordinates.transposed;
	}

	private VisTypes.Coordinates makeCombinedCoords(VisSingle[] elements, VisTypes.Diagram diagram) {
		// For diagrams, we set the coords to polar for the chord chart and clouds, and centered for networks
		if (diagram == VisTypes.Diagram.chord || diagram == VisTypes.Diagram.cloud)
			return VisTypes.Coordinates.polar;

		// The rule here is that we return the one with the highest ordinal value;
		// that will correspond to the most "unusual". In practice this means that
		// you need only define 'polar' or 'transpose' in one chart
		VisTypes.Coordinates result = elements[0].coords;
		for (VisSingle e : elements) if (e.coords.compareTo(result) > 0) result = e.coords;
		return result;
	}

	private double getDefinedExtent(List<Param> items, boolean min) {
		for (Param p : items)
			if (p.isField() && p.hasModifiers()) {
				for (Param q : p.modifiers()) {
					if (q.type() == Param.Type.list) {
						List<Param> extent = q.asList();
						if (extent.size() == 2) {
							Param e = extent.get(min ? 0 : 1);
							try {
								return e.asDouble();
							} catch (Exception ignored) {
								// Bad value or missing-- ignore it
							}
						}
					}
				}
			}
		return min ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
	}

	private Field[] getXFields(VisSingle vis, Dataset data) {
		Field[] result = new Field[vis.fX.size()];
		for (int i = 0; i < vis.fX.size(); i++)
			result[i] = data.field(vis.fX.get(i).asField());
		return result;
	}

	private Field[] getYFields(VisSingle vis, Dataset data) {
		if (vis.fRange != null) {
			// Range is a pair
			return new Field[]{data.field(vis.fRange[0].asField(data)),
					data.field(vis.fRange[1].asField(data))};
		} else if (vis.fY.isEmpty()) {
			return new Field[0];
		} else if (vis.fY.size() > 1 && data.field("#values") != null) {
			// Handle series when they have been used
			if (vis.stacked)
				return data.fieldArray(new String[]{"#values$lower", "#values$upper"});
			else
				return data.fieldArray(new String[]{"#values"});
		}

		// We have a single Y field
		String s = vis.fY.get(0).asField();
		if (vis.stacked) {
			// Stacked has been handled by adding two new fields, so add them
			return data.fieldArray(new String[]{s + "$lower", s + "$upper"});
		} else {
			// Simple case, a single y field
			return data.fieldArray(new String[]{s});
		}
	}

	private String getDefinedXTransform(VisSingle v) {
		if (v.tDiagram != null) return "linear";                         // Diagrams are always linear
		for (Param p : v.fX)
			if (p.isField() && p.hasModifiers()) {
				String type = extractTransform(p);
				if (type != null) return type;
			}
		return null;
	}

	private String extractTransform(Param p) {
		if (p.hasModifierOption("log")) return "log";
		if (p.hasModifierOption("linear")) return "linear";
		if (p.hasModifierOption("root")) return "root";
		return null;
	}

	private String getDefinedYTransform(VisSingle v) {
		if (v.tDiagram != null) return "linear";                         // Diagrams are always linear
		for (Param p : v.fY)
			if (p.isField() && p.hasModifiers()) {
				String s = extractTransform(p);
				if (s != null) return s;
			}
		if (v.fRange != null) {
			if (v.fRange[0].isField() && v.fRange[0].hasModifiers()) {
				String s = extractTransform(v.fRange[0]);
				if (s != null) return s;
			}
			if (v.fRange[1].isField() && v.fRange[1].hasModifiers()) {
				String s = extractTransform(v.fRange[1]);
				if (s != null) return s;
			}
		}
		return null;
	}

	private String chooseTransform(Field[] fields) {
		if (fields.length == 0) return "linear";

		// Go for the transform that "does the most": log > root > linear
		String best = "linear";
		double min = Double.MAX_VALUE;
		for (Field f : fields) {
			if (f.min() == null) continue;
			Auto.setTransform(f);
			String s = f.strProperty("transform");
			if ("log".equals(s)) best = "log";
			else if ("root".equals(s) && !best.equals("log")) best = "root";
			if (f.isNumeric())
				min = Math.min(min, f.min());
		}
		if ("log".equals(best) && min <= 0) return "linear";
		return best;
	}

	public Field[] getX(VisSingle vis) {
		return x.get(vis);
	}

	public Field[] getY(VisSingle vis) {
		return y.get(vis);
	}

}
