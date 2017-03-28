
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

package org.brunel.util;

import org.brunel.action.Action;
import org.brunel.data.Field;
import org.brunel.maps.GeoInformation;
import org.brunel.util.LibraryItem.Param;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Library {

    private static final String DEFAULT_ITEMS = "/org/brunel/util/library-items.txt";

    private static Library STANDARD;

    public static Action choose(Field... selected) {
        return standard().chooseAction(selected);
    }

    /**
     * This is the main method that should be called to provide the most appropriate action
     * @param selected fields to use
     * @return Brunel Action to use to display (null if one cannot be found)
     */
    public Action chooseAction(Field[] selected) {
        if (selected == null || selected.length == 0) return null;
        if (selected.length == 1) return chooseUnivariate(selected[0]);
        if (selected.length == 2) return chooseBivariate(selected[0], selected[1]);
        return chooseMultivariate(selected);
    }

    public Action make(String name, Field... fields) {
        LibraryItem item = get(name);
        return item.apply(fields);
    }

    private LibraryItem get(String name) {
        LibraryItem item = store.get(name);
        if (item == null) throw new NullPointerException("Unknown library item: " + name);
        return item;
    }

    public static Library standard() {
        synchronized (Library.class) {
            if (STANDARD == null) {
                STANDARD = new Library();
                STANDARD.addItems(Library.class.getResourceAsStream(DEFAULT_ITEMS));
            }
            return STANDARD;
        }
    }

    /**
     * Make a custom library with the listed sources. Each source is read from the URL using the 'addItems' method
     */
    public static Library custom(URL... sources) {
        Library library = new Library();
        try {
            for (URL url : sources)
                library.addItems(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return library;
    }

    private final Map<String, LibraryItem> store = new HashMap<>();

    private void add(LibraryItem item) {
        store.put(item.name(), item);
    }

    /**
     * Add to the library by reading from a text resource. The format of the items should be as follows:
     * is NAME :: PARAMETERIZED_ACTION :: PARAMETERS_LIST
     * E.g.
     * scatter	    ::	point x($1) y($2)   ::	field, field
     * line	        ::	line x($1) y($2)    ::	field, field
     *
     * Any white space characters, including new lines, are allowed around the "::" separators
     *
     * @param inputStream items to read
     */
    public void addItems(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("[\r\n\t ]+::[\n\t ]+|\n");
        while (scanner.hasNext()) {
            String name = scanner.next();
            String action = scanner.next();
            Param[] params = toParams(scanner.next().split(","));
            add(new LibraryItem(name, action, params));
        }

    }

    private Param[] toParams(String[] paramTexts) {
        Param[] params = new Param[paramTexts.length];
        for (int i = 0; i < params.length; i++)
            params[i] = Param.valueOf(paramTexts[i].trim());
        return params;
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
