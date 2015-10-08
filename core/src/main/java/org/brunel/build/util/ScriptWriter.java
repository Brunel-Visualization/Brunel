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

package org.brunel.build.util;

import org.brunel.data.Data;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A class to make it easier to output Javascript
 */
public class ScriptWriter {

    private final int lineMaxLength;
    private static final Set<Character> NO_SPACE_BEFORE = new HashSet<Character>(Arrays.asList(':', ',', ';', '(', ')', ']'));
    private static final String INDENT = "  ";
    private final PrintWriter out;
    private final StringWriter base;
    private int consecutiveNewLines = 0;
    private int indentLevel = 0;
    private boolean changed;
    public boolean readable = false;

    public ScriptWriter(boolean readableJavascript) {
        readable = readableJavascript;
        lineMaxLength = readable ? 100 : 400;
        base = new StringWriter();
        out = new PrintWriter(base);
        consecutiveNewLines = 1;
    }

    public ScriptWriter addChained(Object... items) {
        if (readable)
            return indentMore().onNewLine().add(".").add(items).indentLess();
        else
            return add(".").add(items);
    }

    public boolean changedSinceMark() {
        return changed;
    }

    public ScriptWriter continueOnNextLine(String... before) {
        for (String s : before) out.print(s);
        ln();
        if (readable) out.print(INDENT);
        return this;
    }

    public ScriptWriter indentLess() {
        indentLevel--;
        return this;
    }

    public ScriptWriter add(Object... items) {
        // Add indentation if needed
        if (readable && consecutiveNewLines > 0)
            for (int i = 0; i < indentLevel; i++) out.print(INDENT);

        // Add items
        for (int i = 0; i < items.length; i++) {
            Object item = items[i];
            String s;
            if (item == null) {
                s = "null";
            } else if (item instanceof Object[]) {
                s = Data.join((Object[]) item);
            } else if (item instanceof double[]) {
                s = Data.join((double[]) item);
            } else if (item.getClass().isArray()) {
                throw new IllegalStateException("Cannot handle array of type: " + item.getClass());
            } else {
                s = Data.format(item, false);
            }
            if (i > 0 && !NO_SPACE_BEFORE.contains(s.charAt(0))) out.print(" ");
            out.print(s);
        }
        consecutiveNewLines = 0;
        changed = true;
        return this;
    }

    public void mark() {
        changed = false;
    }

    public ScriptWriter onNewLine() {
        if (consecutiveNewLines == 0) ln();
        return this;
    }

    public ScriptWriter indentMore() {
        indentLevel++;
        return this;
    }

    public ScriptWriter ln() {
        consecutiveNewLines++;
        out.println();
        changed = true;
        return this;
    }

    public ScriptWriter addQuoted(Object... items) {
        indentMore().indentMore();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) out.print(readable ? ", " : ",");
            if (currentColumn() > 77) ln();
            add(quote(items[i]));
        }
        indentLess().indentLess();
        return this;
    }

    private int currentColumn() {
        base.flush();
        StringBuffer b = base.getBuffer();
        int end = b.length() - 1;
        int eol = b.lastIndexOf("\n");
        if (eol < 0) eol = 0;
        return end - eol;
    }

    public String quote(Object item) {
        return item == null ? "null" : Data.quote(item.toString());
    }

    public ScriptWriter addQuotedCollection(Collection<?> items) {
        indentMore().indentMore();
        boolean first = true;
        for (Object o : items) {
            if (!first) out.print(", ");
            if (currentColumn() > lineMaxLength - 4) ln();
            add(quote(o));
            first = false;
        }
        indentLess().indentLess();
        return this;
    }

    /**
     * Add spaces until we get to the designated column
     *
     * @param n column index
     * @return this
     */
    public ScriptWriter at(int n) {
        if (readable)
            for (int i = currentColumn(); i < n; i++) add(" ");
        return this;
    }

    public ScriptWriter comment(Object... items) {
        if (readable) {
            if (consecutiveNewLines == 0) add(" ");
            add("// ").add(items);
        }
        ln();
        return this;
    }

    public String content() {
        out.close();
        return base.toString();
    }

    public ScriptWriter endStatement() {
        return add(";").ln();
    }

    public ScriptWriter titleComment(Object... items) {
        ensureBlankLine();
        if (readable) {
            add("// ").add(items).add(" ");
            for (int i = currentColumn(); i < lineMaxLength; i++) add("/");
            ensureBlankLine();
        }
        return this;
    }

    private void ensureBlankLine() {
        if (readable) while (consecutiveNewLines < 2) ln();
        else if (consecutiveNewLines == 0) ln();
    }
}
