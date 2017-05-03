
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

package org.brunel.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.brunel.action.Action;
import org.brunel.data.Field;
import org.brunel.maps.GeoInformation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class provides a means of creating libraries of charts that are easy to apply using a "chart type"
 * paradigm.
 */
public class Library {

	private static final String DEFAULT_ITEMS = "/org/brunel/library/standard-library-items.json";

	/**
	 * Return the standard library of items.
	 * The standard library is shared by all callers of this method. It is functionally the same
	 * library as you would get by calling
	 * <code>
	 * Library.make( Library.standardLibraryContent() );
	 * </code>
	 * It has not been tested for thread safety, but as it does not keep state, it should be fine.
	 *
	 * @return the standard library
	 */
	public static Library standard() {
		synchronized (Library.class) {
			if (STANDARD_INSTANCE == null)
				STANDARD_INSTANCE = make(standardLibraryContent());
			return STANDARD_INSTANCE;
		}
	}

	/**
	 * Return a URL pointing to the standard items
	 *
	 * @return URL to access standard library content
	 */
	public static URL standardLibraryContent() {
		return Library.class.getResource(DEFAULT_ITEMS);
	}

	/**
	 * Build a custom library
	 *
	 * @param contents URLs that will be read to build the items in the library
	 * @return custom library with items read from the contents
	 */
	public static Library make(URL... contents) {
		if (contents.length == 0) throw new IllegalArgumentException("At least one URL for content must be defined");
		Library library = new Library();
		for (URL content : contents) library.addItems(content);
		return library;
	}

	private static Library STANDARD_INSTANCE;

	/**
	 * This is the main method that should be called to provide the most appropriate actions.
	 * It returns a ranked list of possible actions to use, the first being the best.
	 *
	 * @param fields fields to use
	 * @return sorted array of Actions; will not be null but may be empty
	 */
	public LibraryAction[] chooseAction(Field... fields) {
		List<LibraryAction> actions = new ArrayList<>();

		for (LibraryItem item : store.values()) {
			double score = item.suitability(fields);
			if (score > 0) actions.add(item.apply(fields, score));
		}
		Collections.sort(actions);
		return actions.toArray(new LibraryAction[actions.size()]);
	}

	public LibraryAction make(String name, Field... fields) {
		LibraryItem item = get(name);
		if (item == null) throw new IllegalArgumentException("No such library item: " + name);
		return item.apply(fields);
	}

	private LibraryItem get(String name) {
		LibraryItem item = store.get(name);
		if (item == null) throw new NullPointerException("Unknown library item: " + name);
		return item;
	}

	private final Map<String, LibraryItem> store = new HashMap<>();

	private void add(LibraryItem item) {
		store.put(item.toString(), item);
	}

	private void addItems(URL url) {

		try (InputStreamReader reader = new InputStreamReader(url.openStream(), Charset.forName("UTF-8"))) {
			JsonArray items = new JsonParser().parse(reader).getAsJsonObject().get("items").getAsJsonArray();
			for (int i = 0; i < items.size(); i++) {
				JsonObject o = items.get(i).getAsJsonObject();
				String name = o.get("name").getAsString();
				String description = o.get("description").getAsString();
				String action = o.get("action").getAsString();
				double desirability = o.get("desirability").getAsDouble();
				String[] fields = toStringArray(o.get("fields").getAsJsonArray());
				add(new LibraryItem(name, description, action, desirability, fields));
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to read library items from " + url, e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Library  JSON objects containing an array 'items' of json library item descriptions", e);
		}
	}

	private String[] toStringArray(JsonArray array) {
		String[] result = new String[array.size()];
		for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsString();
		return result;
	}

	private Action chooseBivariate(Field a, Field b) {
		// Ensure that if they are of different types, the categorical one is first
		if (b.preferCategorical() && !a.preferCategorical()) return chooseBivariate(b, a);

		// Dates need line charts
		if (a.isDate() && !b.isDate()) return makeLineChart(a, b);
		if (b.isDate() && !a.isDate() && !a.preferCategorical()) return makeLineChart(b, a);

		// See if a choropleth will work
		if (goodForMapNames(a)) return get("colorMap").apply(a, b);
		if (goodForMapNames(b)) return get("colorMap").apply(b, a);

		// Heatmaps if either is categorical
		if (a.preferCategorical() || b.preferCategorical())
			return get("heatmap").apply(orderByCoarseness(a, b));

		// Default to scatter
		return get("scatter").apply(orderByCoarseness(a, b));
	}

	private boolean goodForLongitude(Field f) {
		return f.min() > -181 && f.max() <= 181;
	}

	private boolean goodForLatitude(Field f) {
		return f.min() > -91 && f.max() < 91;
	}

	private Action chooseMultivariate(Field... fields) {
		Field[] ordered = orderByCoarseness(fields);
		if (fields.length == 3) {
			Field a = ordered[0];
			Field b = ordered[1];
			Field c = ordered[2];
			if (!a.preferCategorical() && !b.preferCategorical() && !c.preferCategorical()) {
				return get("bubble").apply(ordered);
			}

		}
		return get("treemap").apply(ordered);
	}

	private Action chooseUnivariate(Field f) {
		if (goodForMapNames(f))
			return get("map").apply(f);
		else if (goodForWordle(f))
			return get("wordle").apply(f);
		else
			return get("barOfCounts").apply(f);
	}

	private boolean goodForMapNames(Field f) {
		return GeoInformation.fractionGeoNames(f) > 0.5;
	}

	private boolean goodForWordle(Field f) {
		if (!f.preferCategorical()) return false;
		if (f.numProperty("unique") > 100 || f.numProperty("unique") < 7) return false;
		// Too long names are not good
		for (Object c : f.categories())
			if (c.toString().length() > 20) return false;
		return true;
	}

	private Action makeLineChart(Field x, Field y) {
		// If there is close to one data point per x coordinate just use lines
		if (x.numProperty("unique") > 0.95 * x.numProperty("validNumeric"))
			return get("line").apply(x, y);
		// Otherwise show lines and points
		return get("lineWithPoints").apply(x, y);
	}

	private Field[] orderByCoarseness(Field... fields) {
		FieldCoarseness.sort(fields);
		return fields;
	}

}
