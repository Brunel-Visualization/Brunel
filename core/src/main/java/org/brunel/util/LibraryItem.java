
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

import java.util.ArrayList;
import java.util.List;

class LibraryItem {

    private final String name;
    private final String action;
    private final Param[] parameters;

    public LibraryItem(String name, String action, Param... parameters) {
        this.name = name;
        this.action = action;
        this.parameters = parameters;
    }

    public Action apply(Field... fields) {

        String text = action;
        List<Field> toBin = new ArrayList<Field>();

        // Step through the parameters
        for (int i = 0; i < parameters.length; i++) {
            Param parameter = parameters[i];
            if (parameter.isMultiple) {
                if (i != parameters.length - 1) throw new IllegalStateException("Multi field must be last parameter");
                // Modify text
                text = modifyStringForMulti(text, fields, i);
                // Check for necessary binning
                for (int j = i; j < fields.length; j++)
                    if (parameter == Param.multiCategorical && !fields[j].preferCategorical()) toBin.add(fields[j]);
            } else {
                // Modify text
                text = text.replaceAll("\\$" + (i + 1), fields[i].name);
                // Check for necessary binning
                if (parameter == Param.categorical && !fields[i].preferCategorical())
                    toBin.add(fields[i]);
            }
        }

        // Make and apply binning
        Action a = Action.parse(text);
        for (Field f : toBin)
            a = a.append(Action.parse("bin(" + f.name + ")"));
        return a.simplify();
    }

    public String name() {
        return name;
    }

    private String modifyStringForMulti(String text, Field[] fields, int start) {
        int n = fields.length - start;

        String all = "";

        for (int i = 0; i < n; i++) {
            // Replace all the [] offsets, both from start and end
            text = text.replaceAll("\\$" + (start + 1) + "\\[" + i + "\\]", fields[start + i].name);
            text = text.replaceAll("\\$" + (start + 1) + "\\[-" + i + "\\]", fields[fields.length - 1 - i].name);

            // Add to 'all' string
            if (i > 0) all = all + ", ";
            all = all + fields[start + i].name;
        }

        // And replace any multis
        return text.replaceAll("\\$" + (start + 1), all);
    }

    enum Param {
        field(false), numeric(false), categorical(false), multiple(true), multiCategorical(true);

        public final boolean isMultiple;

        Param(boolean isMultiple) {
            this.isMultiple = isMultiple;
        }
    }
}
