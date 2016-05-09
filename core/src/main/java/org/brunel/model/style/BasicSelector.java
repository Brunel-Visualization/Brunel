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

import org.brunel.data.Data;

/**
 * Simple selector with a (possibly null) element and zero or more parts
 */
class BasicSelector extends StyleSelector {
    private final String element;
    private final String[] classes;

    public BasicSelector(String element, String[] classes) {
        super(classes.length * 1000 + (element == null || element.isEmpty() ? 0 : 1));
        this.element = element == null || element.isEmpty() ? null : element;
        this.classes = classes;
    }

    public String debug() {
        String s = "{";
        if (element != null) s += element + " ";
        return s + "classes=[" + Data.join(classes, ",") + "]}";
    }

    public boolean match(StyleTarget target) {
        // If this has an element, it must org.brunel.app.match
        if (element != null && !element.equals(target.element)) return false;
        for (String s : classes) if (unmatched(s, target.classes)) return false;
        return true;
    }

    public StyleSelector replaceClass(String target, String replace) {
        String[] replaced = new String[classes.length];
        boolean changed = false;
        for (int i = 0; i < replaced.length; i++) {
            if (classes[i].equals(target)) {
                replaced[i] = replace;
                changed = true;
            } else {
                replaced[i] = classes[i];
            }
        }
        return changed ? new BasicSelector(element, replaced) : this;
    }

    private boolean unmatched(String c, String[] classes) {
        for (String s : classes) if (c.equals(s)) return false;
        return true;
    }

    public String toString() {
        String txt = element == null ? "" : element;
        for (String s : classes) txt += "." + s;
        return txt;
    }
}
