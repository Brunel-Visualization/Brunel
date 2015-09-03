
/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.util;

import org.brunel.action.Action;
import org.brunel.data.Field;
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
        Library lib = standard();
        if (selected == null || selected.length == 0) return null;
        if (selected.length == 1) return lib.chooseUnivariate(selected[0]);
        if (selected.length == 2) return lib.chooseBivariate(selected[0], selected[1]);
        return lib.chooseMultivariate(selected);
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

    private final Map<String, LibraryItem> store = new HashMap<String, LibraryItem>();

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

    private Action chooseBivariate(Field x, Field y) {
        // Dates need line charts
        if (x.hasProperty("date") && !y.hasProperty("date") && !y.preferCategorical()) return makeLineChart(x, y);
        if (y.hasProperty("date") && !x.hasProperty("date") && !x.preferCategorical()) return makeLineChart(y, x);
        // Heatmaps if either is categorical
        if (x.preferCategorical() || y.preferCategorical())
            return get("heatmap").apply(orderByCoarseness(x, y));
        // Default to scatter
        return get("scatter").apply(orderByCoarseness(x, y));
    }

    private Action chooseMultivariate(Field... fields) {
        Field[] ordered = orderByCoarseness(fields);
        if (fields.length == 3) {
            if (!ordered[0].preferCategorical() && !ordered[1].preferCategorical() && !ordered[2].preferCategorical())
                return get("bubble").apply(ordered);
        }
        return get("treemap").apply(ordered);
    }

    private Action chooseUnivariate(Field f) {
        if (goodForWordle(f))
            return get("wordle").apply(f);
        else
            return get("barOfCounts").apply(f);
    }

    private boolean goodForWordle(Field f) {
        if (!f.preferCategorical()) return false;
        if (f.getNumericProperty("unique") > 100 || f.getNumericProperty("unique") < 7) return false;
        // Too long names are not good
        for (Object c : f.categories())
            if (c.toString().length() > 20) return false;
        return true;
    }

    private Action makeLineChart(Field x, Field y) {
        // If there is close to one data point per x coordinate just use lines
        if (x.getNumericProperty("unique") > 0.95 * x.getNumericProperty("validNumeric"))
            return get("line").apply(x, y);
        // Otherwise show lines and points
        return get("lineWithPoints").apply(x, y);
    }

    private Field[] orderByCoarseness(Field... fields) {
        FieldCoarseness.sort(fields);
        return fields;
    }

}
