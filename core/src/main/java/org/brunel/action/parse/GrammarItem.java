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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores information on how to parse a token
 */
public class GrammarItem {
    public final String name;
    public final String type;
    public final String parameter;                  // If non-null, this is the type of parameter allowed
    public final Set<String> options;               // If non-null, these are the options allowed

    GrammarItem(String[] description) {
        this.name = description[0];
        this.type = description[1];
        if (description.length == 2) {
            // No parameter, no options:      e.g.    stack, bar
            parameter = null;
            options = null;
        } else if (description.length == 3) {
            // Parameter, not options         e.g.    x(a,b,c), size(y)
            parameter = description[2];
            options = null;
        } else {
            // Options, not parameter         e.g.    axes(none), legends(all)
            parameter = null;
            options = new LinkedHashSet<String>();
            for (int i = 2; i < description.length; i++) options.add(description[i]);
        }
    }

}
