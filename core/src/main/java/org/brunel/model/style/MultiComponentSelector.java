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
 * Implements a "a b c" descendant selector
 */
class MultiComponentSelector extends StyleSelector {
    private final StyleSelector[] components;

    public MultiComponentSelector(StyleSelector... components) {
        super(sumSpecificity(components));
        this.components = components;
    }

    private static int sumSpecificity(StyleSelector[] components) {
        int sum = 0;
        for (StyleSelector s : components) sum += s.specificity;
        return sum;
    }

    public StyleSelector containedIn(StyleSelector[] top) {
        StyleSelector[] c = new StyleSelector[top.length + components.length];
        System.arraycopy(top, 0, c, 0, top.length);
        System.arraycopy(components, 0, c, top.length, components.length);
        return new MultiComponentSelector(c);
    }

    public String debug() {
        String s = "";
        for (int i = 0; i < components.length; i++) {
            if (i > 0) s += " > ";
            s += components[i].debug();
        }
        return s;
    }

    public boolean match(StyleTarget target) {
        // Lowest level must match this one
        if (!components[components.length - 1].match(target)) return false;

        int selectorToMatch = components.length - 2;
        target = target.parent;

        // Step though all the components in revese order
        for (int i = components.length - 2; i >= 0; i--) {
            // Try and found a parent match by searching upwards through parent chain
            while (target != null && !components[i].match(target)) target = target.parent;

            // If we did not find a match, we failed, otherwise move to parent and look for next item
            if (target == null) return false;
            target = target.parent;
        }

        // All components matched
        return true;
    }

    public StyleSelector replaceClass(String target, String replace) {
        StyleSelector[] replaced = new StyleSelector[components.length];
        boolean changed = false;
        for (int i = 0; i < replaced.length; i++) {
            replaced[i] = components[i].replaceClass(target, replace);
            if (replaced[i] != components[i]) changed = true;
        }
        return changed ? new MultiComponentSelector(replaced) : this;
    }

    public String toString() {
        return Data.join(components, " ", false);
    }

}
