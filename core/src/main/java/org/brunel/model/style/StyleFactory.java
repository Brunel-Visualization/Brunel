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

package org.brunel.model.style;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;



/*

    NOTES:

    To find the value for an element/property combination:

    [1] Find all declarations that apply to the element and property in question
        Declarations apply if the associated selector matches the element in question
    [2] Sort in ascending order of precedence by properties tagged as ! important
    [3] Sort in ascending order of precedence by origin
        [a] brunel default declarations
        [b] styles set into brunel objects
        [c] styles defined by the brunel action language
    [4] When the origin is the same, sort by specificity of selector; more specific overrides more general
    [5] Finally, sort by order specified: the latter specified wins

    Sorting by specificity:
        [a] count the number of class names and non-element attributes (.class1, .class2, ...)
        [b] count the number of elements (text, rect, ...)

*/

/**
 * For handling style sheets. This uses a singleton pattern and makes sure it never has more than
 * than a fix number of cached items
 */
public class StyleFactory {

    private final static StyleFactory INSTANCE = new StyleFactory(400);

    public static StyleFactory instance() {
        return INSTANCE;
    }

    final int maxEntries;

    // Adds an override to limit the growth
    final LinkedHashMap<String, StyleSelector> selectors = new LinkedHashMap<String, StyleSelector>() {
        protected boolean removeEldestEntry(Map.Entry<String, StyleSelector> eldest) {
            return size() > maxEntries;
        }
    };

    private StyleFactory(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Makes a style sheet, silently discarding any non-conforming syntax
     *
     * @param text input style sheet
     * @return processed form
     */
    public StyleSheet makeStyleSheet(String text) {
        StyleSheet sheet = new StyleSheet();
        for (String s : text.split("\\}")) {
            // This should be of the form "label { a:foo" (missing last brace)
            String[] parts = s.split("\\{");
            if (parts.length != 2) continue;
            // parts[] should be the selector and options
            StyleSelector[] selectors = makeSelectors(parts[0]);
            Map<String, String> options = makeOptions(parts[1]);
            for (StyleSelector sel : selectors) sheet.addEntry(sel, options);
        }
        return sheet;
    }

    /**
     * Return one or more selectors for the given text
     *
     * @param text selector text
     * @return parsed selectors, one for each comma-divided entry
     */
    public StyleSelector[] makeSelectors(String text) {
        String[] parts = text.split(",");
        StyleSelector[] result = new StyleSelector[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = makeSingleSelector(parts[i].trim());
        return result;
    }

    private Map<String, String> makeOptions(String text) {
        // text looks like this "a:b; c:d" ...
        HashMap<String, String> map = new HashMap<String, String>();
        for (String item : text.split(";")) {
            String[] keyValue = item.split(":");
            // Ignore bad syntax items
            if (keyValue.length == 2) {
                String a = keyValue[0].trim();
                String b = keyValue[1].trim();
                if (!a.isEmpty() && !b.isEmpty()) map.put(a, b);
            }
        }
        return map;
    }

    StyleSelector makeSingleSelector(String text) {
        StyleSelector s = selectors.get(text);
        if (s == null) {
            String[] parts = text.split("[ \t]+");
            if (parts.length == 1)
                s = makeSingleComponentSelector(parts[0]);
            else {
                StyleSelector[] components = new StyleSelector[parts.length];
                for (int i = 0; i < parts.length; i++) components[i] = makeSingleComponentSelector(parts[i].trim());
                s = new MultiComponentSelector(components);
            }
            selectors.put(text, s);
        }
        return s;
    }

    private StyleSelector makeSingleComponentSelector(String text) {
        if (text.equals("*")) return new AnySelector();
        String[] parts = text.split("\\.");
        if (parts.length == 1) return new BasicSelector(parts[0], new String[0]);
        if (parts.length == 2) return new BasicSelector(parts[0], new String[]{parts[1]});
        String[] classes = new String[parts.length - 1];
        System.arraycopy(parts, 1, classes, 0, parts.length - 1);
        return new BasicSelector(parts[0], classes);
    }
}
