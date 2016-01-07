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

package org.brunel.action.parse;

import org.brunel.data.Data;

import java.util.Set;
import java.util.TreeSet;

/**
 * Stores information on how to parse a token
 */
public class GrammarItem {
    public final String name;
    public final String type;
    private final Set<String> parameters;            // These are the type of parameter allowed
    private final Set<String> options;               // These are the options allowed
    private final boolean emptyAllowed;              // If true, may have no parameters
    private final boolean multiOptionsAllowed;       // If true, multiple options can be specified

    GrammarItem(String[] description) {
        this.name = description[0];
        this.type = description[1];
        this.parameters = new TreeSet<String>();
        this.options = new TreeSet<String>();

        boolean canBeEmpty = false, canBeMulti = false;
        for (int i = 2; i < description.length; i++) {
            String s = description[i];
            if (s.equalsIgnoreCase("-"))                        // - means "may be empty"
                canBeEmpty = true;
            else if (s.equalsIgnoreCase("+"))                   // + means "may have multiple options"
                canBeMulti = true;
            else if (s.toUpperCase().equals(s))                 // ALL CAPS such as LITERAL, STRING, FIELD, etc.
                parameters.add(s);
            else
                options.add(s);                                 // Rest are options that are allowed
        }
        this.emptyAllowed = canBeEmpty || hasNoContent();
        this.multiOptionsAllowed = canBeMulti;
    }

    public boolean allowsMultiples() {
        if (multiOptionsAllowed) return true;
        for (String s : parameters) if (s.endsWith("+")) return true;
        return false;
    }

    public boolean allowsParameter(String s, boolean secondAppearance) {
        return parameters.contains(s + "+") || !secondAppearance && parameters.contains(s);
    }

    public boolean hasNoContent() {
        return parameters.isEmpty() && options.isEmpty();
    }

    public boolean isOption(String s) {
        return options.contains(s);
    }

    public boolean mayHaveNoContent() {
        return emptyAllowed;
    }

    public boolean mayHaveMultipleOptions() {
        return multiOptionsAllowed;
    }

    public String options() {
        return Data.join(options);
    }
}
