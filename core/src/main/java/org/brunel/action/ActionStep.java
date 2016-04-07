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

package org.brunel.action;

import org.brunel.action.parse.ParseGrammar;
import org.brunel.data.Data;
import org.brunel.model.VisException;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

import java.util.Arrays;
import java.util.Set;

public class ActionStep {
    // Use the parser to retrieve the list of summary and transform methods
    private static final Set<String> SUMMARY_METHODS = ParseGrammar.instance().getSummaryMethods();
    private static final Set<String> TRANSFORM_METHODS = ParseGrammar.instance().getTransformMethods();

    public final String name;
    public final Param[] parameters;

    ActionStep(String actionName, Param... params) {
        name = actionName.toLowerCase();
        this.parameters = params;
    }

    public int hashCode() {
        return 31 * name.hashCode() + Arrays.hashCode(parameters);
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ActionStep)) return false;
        ActionStep other = (ActionStep) obj;
        return name.equals(other.name) && Arrays.equals(parameters, other.parameters);
    }

    public String toString() {
        if (parameters.length == 0) return name;
        return name + '(' + Data.join(parameters) + ')';
    }

    VisSingle apply(VisSingle item) {
        try {
            if (name.equals("x")) {
                item.x(parameters);
                return item;
            } else if (name.equals("y")) {
                item.y(parameters);
                return item;
            } else if (name.equals("yrange")) {
                if (parameters.length < 1 || parameters.length > 2)
                    throw new IllegalArgumentException("yrange requires 1-2 fields");
                if (parameters.length == 1)
                    item.yrange(parameters[0], parameters[0]);
                else
                    item.yrange(parameters[0], parameters[1]);
                return item;
            } else if (name.equals("color")) {
                item.color(parameters);
                return item;
            } else if (name.equals("opacity")) {
                item.opacity(parameters);
                return item;
            } else if (name.equals("size")) {
                item.size(parameters);
                return item;
            } else if (name.equals("split")) {
                item.split(parameters);
                return item;
            } else if (name.equals("label")) {
                item.label(parameters);
                return item;
            } else if (name.equals("tooltip")) {
                item.tooltip(parameters);
                return item;
            } else if (TRANSFORM_METHODS.contains(name)) {
                item.transform(name, parameters);
                return item;
            } else if (SUMMARY_METHODS.contains(name)) {
                item.summarize(name, parameters);
                return item;
            } else if (name.equals("sort")) {
                item.sort(parameters);
                return item;
            } else if (name.equals("at")) {
                item.at(parameters);
                return item;
            } else if (name.equals("filter")) {
                item.filter(parameters);
                return item;
            } else if (name.equals("key")) {
                item.key(parameters);
                return item;
            } else if (name.equals("style")) {
                item.style(oneParam());
                return item;
            } else if (name.equals("axes")) {
                item.axes(parameters);
                return item;
            } else if (name.equals("legends")) {
                item.legends(oneParam());
                return item;
            } else if (name.equals("interaction")) {
                item.interaction(parameters);
                return item;
            } else if (name.equals("transpose")) {
                item.transpose();
                return item;
            } else if (name.equals("polar")) {
                item.polar();
                return item;
            } else if (name.equals("stack")) {
                item.stack();
                return item;
            } else if (name.equals("using")) {
                item.using(oneParam());
                return item;
            } else if (name.equals("flipx")) {
                item.flipx();
                return item;
            } else if (name.equals("flip")) {
                item.flip();
                return item;
            } else if (name.equals("data")) {
                item.data(oneParam());
                return item;
            }

            else if (name.equals("annotation")) {
                // Ignore it
                return item;
            }

            for (Element t : Element.values())
                if (t.name().equals(name)) {
                    item.element(t);
                    return item;
                }
            for (Diagram t : Diagram.values())
                if (t.name().equals(name)) {
                    item.diagram(t, parameters);
                    return item;
                }

            throw new IllegalArgumentException("Cannot apply '" + name + "' to this item");

        } catch (Exception ex) {
            throw VisException.makeApplying(ex, this);
        }

    }

    private Param oneParam() {
        return parameters.length > 0 ? parameters[parameters.length - 1] : null;
    }

}
