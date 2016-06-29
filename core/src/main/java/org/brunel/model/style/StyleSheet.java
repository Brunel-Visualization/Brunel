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

import org.brunel.build.Builder;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A style sheet
 */
public class StyleSheet {
    private static StyleSheet brunelDefault;        // The Default one for Brunel

    private static void ensureDefaultBuilt() {
        if (brunelDefault == null) {
            // Create this when it is needed
            String text = new Scanner(Builder.class.getResourceAsStream("/javascript/Brunel.css"), "UTF-8").useDelimiter("\\A").next();
            brunelDefault = StyleFactory.instance().makeStyleSheet(text);
        }
    }

    public void add(StyleSheet other, String... parentClasses) {
        if (other == null) return;
        if (parentClasses.length == 0) {
            // Simple copy
            for (StyleSheetEntry e : other.entries)
                entries.add(new StyleSheetEntry(e.selector, e.options, entries.size()));
        } else {
            // Build into simple parts identifying parents
            StyleSelector[] parts = new StyleSelector[parentClasses.length];
            for (int i = 0; i < parts.length; i++)
                parts[i] = new BasicSelector("", new String[]{parentClasses[i]});

            for (StyleSheetEntry e : other.entries)
                entries.add(new StyleSheetEntry(e.selector.containedIn(parts), e.options, entries.size()));
        }
        sorted = false;
    }

    public static String getBrunelDefault(StyleTarget target, String key) {
        ensureDefaultBuilt();
        return brunelDefault.get(target, key);
    }

    public String get(StyleTarget target, String key) {
        ensureSorted();
        for (StyleSheetEntry e : entries) {
            if (e.selector.match(target)) {
                String v = e.options.get(key);
                if (v != null) return v;
            }
        }
        return null;
    }

    /* Returns a new style sheet with a class replaced */
    public StyleSheet replaceClass(String target, String replace) {
        StyleSheet result = new StyleSheet();
        for (StyleSheetEntry style : entries)
            result.entries.add(style.replaceClass(target, replace));
        return result;
    }

    private void ensureSorted() {
        if (sorted) return;
        Collections.sort(entries);
    }

    private final List<StyleSheetEntry> entries;    // Entries
    private boolean sorted;                         // If true, most specific first

    public StyleSheet() {
        entries = new ArrayList<>();
        sorted = false;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void add(String text) {
        add(StyleFactory.instance().makeStyleSheet(text));
    }

    public void clear() {
        entries.clear();
    }

    public Map<String, String> stylesFor(StyleTarget parent, String type, String... classes) {
        ensureSorted();
        StyleTarget target = StyleTarget.makeTarget(type, parent, classes);
        Map<String, String> result = new TreeMap<>();

        // Only add the first occurrences of each org.brunel.app.match as they override each color in order
        for (StyleSheetEntry e : entries) {
            if (e.selector.match(target)) {
                for (Entry<String, String> o : e.options.entrySet()) {
                    if (!result.containsKey(o.getKey())) result.put(o.getKey(), o.getValue());
                }
            }
        }
        return result;
    }

    public String toString() {
        return toString(null);
    }

    public String toString(String owner) {
        StringWriter w = new StringWriter();
        try {
            write(w, owner);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return w.toString().trim();
    }

    /**
     * Write the stylesheet, with an optional super-parent
     *
     * @param writer where to write to
     * @param owner  if defined, this is a "super-parent' added before everything
     * @throws IOException
     */
    private void write(Writer writer, String owner) throws IOException {
        // Must write in REVERSE order; most important last
        ensureSorted();
        Collections.reverse(entries);

        for (StyleSheetEntry e : entries) {
            if (e != entries.get(0)) writer.write('\n');
            if (owner != null) writer.write(owner + " ");
            writer.write(e.selector.toString());
            writer.write(" {");
            writer.append('\n');
            Set<String> keys = new TreeSet<>(e.options.keySet());
            for (String key : keys) {
                writer.write("\t");
                writer.write(key);
                writer.write(": ");
                writer.write(e.options.get(key));
                writer.write(";\n");
            }
            writer.write("}\n");

        }

        // restore correct order
        Collections.reverse(entries);
    }

    void addEntry(StyleSelector selector, Map<String, String> options) {
        entries.add(new StyleSheetEntry(selector, options, entries.size()));
        sorted = false;
    }

    private class StyleSheetEntry implements Comparable<StyleSheetEntry> {
        private final StyleSelector selector;
        private final Map<String, String> options;
        private final int index;

        public StyleSheetEntry(StyleSelector selector, Map<String, String> options, int index) {
            this.selector = selector;
            this.options = options;
            this.index = index;
        }

        public int compareTo(StyleSheetEntry o) {
            // The selector's specificity determines the comparison, then the order they were added
            int n = selector.compareTo(o.selector);
            return n == 0 ? o.index - index : n;
        }

        public StyleSheetEntry replaceClass(String target, String replace) {
            StyleSelector sel = selector.replaceClass(target, replace);
            if (sel == selector) return this;
            return new StyleSheetEntry(sel, options, index);
        }
    }
}
