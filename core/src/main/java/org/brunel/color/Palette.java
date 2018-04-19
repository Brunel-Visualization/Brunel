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

package org.brunel.color;

import org.brunel.action.Param;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.util.DateUnit;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Palettes of color
 */
public class Palette {

	private static final Palette[] sequential = new Palette[]{
			new Palette("Blues;blue,purple;#f1eef6,#bdc9e1,#74a9cf,#2b8cbe,#045a8d"),
			new Palette("Greens;blue,green;#edf8fb,#b2e2e2,#66c2a4,#2ca25f,#006d2c"),
			new Palette("PurpleBlues;blue,purple;#edf8fb,#b3cde3,#8c96c6,#8856a7,#810f7c"),
			new Palette("BlueGreens;green,blue;#f0f9e8,#bae4bc,#7bccc4,#43a2ca,#0868ac"),
			new Palette("Reds;orange,red;#fef0d9,#fdcc8a,#fc8d59,#e34a33,#b30000"),
			new Palette("Purples;red,purple;#feebe2,#fbb4b9,#f768a1,#c51b8a,#7a0177"),
			new Palette("GreenYellows;green;#ffffcc,#c2e699,#78c679,#31a354,#006837"),
			new Palette("BlueYellows;green,blue;#ffffcc,#a1dab4,#41b6c4,#2c7fb8,#253494"),
			new Palette("Browns;orange,brown;#ffffd4,#fed98e,#fe9929,#d95f0e,#993404")
	};

	private static final Palette nominal19;                         // 19 of the 22 Kelly colors
	private static final Map<String, String> COLORS_BY_NAME;        // Colors by name

	private static final Palette emptyPalette = new Palette("empty", Collections.EMPTY_LIST, false);

	static {
		COLORS_BY_NAME = new HashMap<>();
		Scanner scanner = new Scanner(Palette.class.getResourceAsStream("/org/brunel/color/colors.txt")).useDelimiter("\n");

		List<String> boyntonColors = new ArrayList<>();
		List<String> kellyColors = new ArrayList<>();
		while (scanner.hasNext()) {
			String s = scanner.next();
			String[] parts = s.split("[\t]+");
			if (parts.length == 3) {
				String namespace = parts[0].trim();
				String name = parts[1].toLowerCase().trim();
				String def = parts[2].trim();
				String prev = COLORS_BY_NAME.put(name, def);
				if (prev != null && !prev.equals(def))
					throw new IllegalStateException("Color file redefined " + name + ". Was " + prev + ", redefined to " + def);
				if (namespace.equalsIgnoreCase("boynton")) boyntonColors.add(def);
				if (namespace.equalsIgnoreCase("kelly")) kellyColors.add(def);
			}
		}
		if (kellyColors.size() != 19)
			throw new IllegalStateException("Expected 19 Kelly colors, but was " + kellyColors.size());
		nominal19 = new Palette("nominal19", kellyColors, false);
	}

	private final String name;
	public final String singleColor;
	private final Set<String> colorTags;
	private final String[] items;

	public Palette(String definition) {
		String[] parts = definition.split(";");
		this.name = parts[0].trim();
		this.colorTags = new HashSet<>();
		for (String s : parts[1].split(",")) colorTags.add(s.trim());
		String[] cols = parts[2].split(",");
		items = new String[cols.length];
		for (int i = 0; i < items.length; i++) items[i] = cols[i].trim();
		this.singleColor = null;
	}

	public Palette(String name, List<String> colors, boolean singleColor) {
		this.name = name;
		this.singleColor = singleColor ? colors.get(colors.size() - 1) : null;
		this.colorTags = Collections.emptySet();
		this.items = colors.toArray(new String[colors.size()]);
	}

	public static ColorMapping makeColorMapping(Field f, Param[] modifiers, boolean largeElement) {
		int mutingLevel = largeElement && f.preferCategorical() && !f.isNumeric() ? 1 : 0;
		ColorMapping base;

		if (modifiers.length == 0) {
			base = makeDefaultMapping(f);
		} else {
			List<Param> params = modifiers[0].asList();
			List<String> names = new ArrayList<>();
			for (Param p : params) {
				String s = p.asString();
				if (s.equals("=")) mutingLevel = 0;
				else if (s.startsWith("*")) mutingLevel += s.length();
				else names.add(s);
			}
			base = makeNamedMapping(f, names);
		}

		// For binning the palette is based on the numeric values, but the categories
		// are not numeric, so we need to adapt the colors to fit the categories
		if (f.isBinned() && f.isNumeric())
			base = base.fitColorsToCategories(f);

		// Mute the colors of the result if necessary
		return mutingLevel == 0 ? base : base.mute(mutingLevel);
	}

	private static ColorMapping makeNamedMapping(Field f, List<String> paletteParts) {
		if (paletteParts.size() == 0) {
			// Default palette probably used just so we can mute the default
			return makeDefaultMapping(f);
		} else if (paletteParts.size() == 1) {
			String name = paletteParts.get(0);
			if (name.equals("diverging")) return makeNamedMapping(f, Arrays.asList("Blues", "Reds"));

			Palette palette = makeNamedPalette(name);
			if (f.isNumeric())
				return new ColorMapping(fieldSplits(f, palette.length()), palette.items);
			else
				return makeNominal(f, palette.items);
		} else if (paletteParts.size() == 2 && f.isNumeric()) {
			// Two range divergent
			String a = paletteParts.get(0);
			String b = paletteParts.get(1);
			return makeDivergent(f, makeNamedPalette(a), makeNamedPalette(b));
		} else {
			// Either individual values or interpolations == but all define exact colors
			List<String> colors = new ArrayList<>();
			for (String s : paletteParts) {
				Palette p = makeNamedPalette(s);
				if (p.singleColor != null) colors.add(p.singleColor);
				else Collections.addAll(colors, p.items);
			}
			Palette combined = new Palette("combined", colors, false);
			if (f.isNumeric())
				return new ColorMapping(fieldSplits(f, combined.length()), combined.items);
			else
				return makeNominal(f, combined.items);
		}
	}

	private int length() {
		return items.length;
	}

	private static ColorMapping makeDefaultMapping(Field f) {
		if (!f.isNumeric()) return makeNamedMapping(f, Collections.singletonList("nominal"));

		// Default to divergent, except for given cases below
		boolean divergent = true;
		if (f.name.startsWith("#")) divergent = false;
		String summary = f.strProperty("summary");
		if ("sum".equals(summary) || "percent".equals(summary)) divergent = false;

		// Ask for the appropriate named mapping
		return makeNamedMapping(f, divergent ?
				Arrays.asList("Blues", "Reds") : Collections.singletonList("Blues"));
	}

	private static Palette makeNamedPalette(String name) {
		if (name.equals("none") || name.isEmpty()) return emptyPalette;
		if (name.equalsIgnoreCase("nominal") || name.equalsIgnoreCase("ordinal")
				|| name.equalsIgnoreCase("nominal19") || name.equalsIgnoreCase("kelly"))
			return nominal19;

		if (name.startsWith("continuous") || name.startsWith("sequential")) {
			int n;
			try {
				n = Data.parseInt(name.substring(name.length() - 1));
			} catch (NumberFormatException e) {
				// The default is the first one
				n = 1;
			}
			n = (n + sequential.length - 1) % sequential.length;
			return sequential[n];
		}

		for (Palette p : sequential)
			if (p.name.equalsIgnoreCase(name)) return p;

		if (name.startsWith("#") && name.length() == 7) return makeSingleHue(name);

		String color = COLORS_BY_NAME.get(name.toLowerCase());
		if (color != null) return makeSingleHue(color);

		throw new IllegalStateException("Unknown color palette: " + name);

	}

	private static Palette makeSingleHue(String c) {
		Color base = Color.decode(c);
		float[] hsv = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), new float[3]);
		int n = 10;
		List<String> colors = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			float r = i / (n - 1.0f);
			Color a = Color.getHSBColor(hsv[0], r * hsv[1], (1 - r) + r * hsv[2]);
			colors.add(String.format("#%02X%02X%02X", a.getRed(), a.getGreen(), a.getBlue()));
		}
		return new Palette(c, colors, true);
	}

	public static Object[] fieldSplits(Field f, int n) {
		Auto.defineTransform(f);
		String t = f.strProperty("transform");
		Object[] objects = new Object[n];
		double min = f.min();
		double max = f.max();
		if (min == max) {
			// For degenerate data, we need to expand the min/max
			DateUnit unit = (DateUnit) f.property("dateUnit");
			if (unit != null) {
				min -= unit.approxDaysPerUnit;
				max += unit.approxDaysPerUnit;
			} else {
				min--;
				max++;
			}
		}
		for (int i = 0; i < n; i++) {
			if ("log".equals(t)) {
				objects[i] = Math.exp(interpolate(n, Math.log(min), Math.log(max), i));
			} else if ("root".equals(t)) {
				objects[i] = Math.pow(interpolate(n, Math.sqrt(min), Math.sqrt(max), i), 2);
			} else {
				objects[i] = interpolate(n, min, max, i);
			}
		}
		return objects;
	}

	private static double interpolate(int n, double min, double max, int i) {
		return min + (max - min) * i / (n - 1);
	}

	private static ColorMapping makeDivergent(Field f, Palette lower, Palette upper) {
		int low = lower.items.length;
		int n = low + upper.items.length - 1;
		String[] colors = new String[n];
		for (int i = 0; i < n; i++) {
			if (i < low - 1) {
				colors[i] = lower.items[low - 1 - i];
			} else if (i == low - 1) {
				colors[i] = mid(lower.items[0], upper.items[0], 0.5);
			} else {
				colors[i] = upper.items[i - low];
			}
		}
		return new ColorMapping(fieldSplits(f, n), colors);
	}

	static String mid(String a, String b, double v) {
		// midway between two colors as hex strings -- just average the parts
		// they should be close, so we should not need to do anything complicated
		return "#" + mid2(a.substring(1, 3), b.substring(1, 3), v)
				+ mid2(a.substring(3, 5), b.substring(3, 5), v) + mid2(a.substring(5, 7), b.substring(5, 7), v);
	}

	static String mid2(String hexA, String hexB, double v) {
		int a = Integer.parseInt(hexA, 16);
		int b = Integer.parseInt(hexB, 16);
		int mid = (int) Math.round(a * (1 - v) + b * v);
		String s = Integer.toHexString(mid);
		return s.length() < 2 ? "0" + s : s;
	}

	private static ColorMapping makeNominal(Field field, String[] items) {
		// Categorical data is easy -- just us the categories and we are done!
		if (field.preferCategorical())
			return new ColorMapping(field.categories(), items);

		// For numeric data we make bands of color
		int n = items.length;
		Object[] simple = fieldSplits(field, n);
		Object[] rampedValues = new Object[2 * n - 2];
		String[] rampedItems = new String[2 * n - 2];
		for (int i = 0; i < n - 1; i++) {
			rampedValues[2 * i] = simple[i];
			rampedValues[2 * i + 1] = simple[i + 1];
			rampedItems[2 * i] = items[i];
			rampedItems[2 * i + 1] = items[i];
		}
		return new ColorMapping(rampedValues, rampedItems);
	}

}
