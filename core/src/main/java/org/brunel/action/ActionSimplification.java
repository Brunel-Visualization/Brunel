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

package org.brunel.action;

import org.brunel.action.parse.GrammarItem;
import org.brunel.action.parse.ParseGrammar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ActionSimplification {

    private static final Map<String, Integer> ORDER;
    private final ArrayList<ActionStep> items;
    private final ParseGrammar grammar;

    public ActionSimplification(ActionStep[] steps) {
        this.grammar = ParseGrammar.instance();
        this.items = new ArrayList<ActionStep>(Arrays.asList(steps));
    }

    public ActionStep[] make() {
        ArrayList<ActionStep> result = new ArrayList<ActionStep>();
        int last = 0;
        for (int i = 0; i < items.size(); i++) {
            String name = items.get(i).name;
            if (name.equals(">") || name.equals(">>") || name.equals("|") || name.equals("+")) {
                // Process up to this point and then add the special item
                ArrayList<ActionStep> base = process(items.subList(last, i));
                result.addAll(base);
                result.add(items.get(i));
                last = i + 1;
            }
        }

        if (last < items.size())
            result.addAll(process(items.subList(last, items.size())));

        return result.toArray(new ActionStep[result.size()]);
    }

    private Set<Object> accumulateNonDataParameters(ArrayList<ActionStep> base) {
        HashSet<Object> hashSet = new HashSet<Object>();
        // Add all parameter content that are not data operations
        for (ActionStep a : base) {
            String actionType = grammar.get(a.name).type;
            if (!actionType.equals("data")) {
                for (Param p : a.parameters) hashSet.add(p.asString());
            }
        }
        return hashSet;
    }

    private boolean canMerge(String name) {
        // We cannot merge "color" commands, but otherwise anything that allows multiple parameters
        GrammarItem def = grammar.get(name);
        return !def.type.equals("color") && def.parameter != null && def.parameter.endsWith("+");
    }

    private void dropAllExceptLast(ArrayList<ActionStep> base, String type, boolean ignoreParameters) {
        // Drop all except the last one to occur
        boolean found = false;
        Param[] lastParams = null;
        for (int i = base.size() - 1; i >= 0; i--) {
            ActionStep single = base.get(i);
            String actionType = grammar.get(single.name).type;
            if (actionType.equals(type) && (!found || ignoreParameters || parametersMatch(single.parameters, lastParams))) {
                if (found)
                    base.remove(i);
                else {
                    found = true;
                    lastParams = single.parameters;
                }
            }
        }
    }

    private void dropAllExceptLastByName(ArrayList<ActionStep> base, String name) {
        // Drop all except the last one to occur
        boolean found = false;
        for (int i = base.size() - 1; i >= 0; i--) {
            String actionName = base.get(i).name;
            if (actionName.equals(name))
                if (found)
                    base.remove(i);
                else
                    found = true;

        }
    }

    private void dropDataParametersNotUsedElsewhere(ArrayList<ActionStep> base, Set<Object> usedParameters) {
        // If an action says bin something, but that something is never used, it is useless
        for (int i = 0; i < base.size(); i++) {
            ActionStep a = base.get(i);
            String actionName = base.get(i).name;
            String actionType = grammar.get(actionName).type;
            if (actionType.equals("data")) {
                Param[] goodParams = keepUsed(a.parameters, usedParameters);
                if (goodParams == null) {
                    base.remove(i);
                    continue;
                }
                if (goodParams != a.parameters) {
                    Arrays.sort(goodParams);
                    base.set(i, ActionUtil.replaceParameters(a, goodParams));
                } else if (a.parameters.length > 1) Arrays.sort(a.parameters);
            }
        }
    }

    private Param[] keepUsed(Param[] params, Set<Object> used) {
        // Deal with common and easy cases first
        if (params.length == 0) return null;
        if (params.length == 1) return used.contains(params[0].asString()) ? params : null;

        Set<Param> toKeep = new HashSet<Param>(params.length);
        for (Param p : params) {
            if (used.contains(p.asString())) toKeep.add(p);
        }
        if (toKeep.size() == params.length) return params;
        return toKeep.toArray(new Param[toKeep.size()]);
    }

    private ActionStep mergeActions(ActionStep a, ActionStep b) {
        assert a.name.equals(b.name);
        Param[] params = new Param[a.parameters.length + b.parameters.length];
        System.arraycopy(a.parameters, 0, params, 0, a.parameters.length);
        System.arraycopy(b.parameters, 0, params, a.parameters.length, b.parameters.length);
        return new ActionStep(a.name, params);
    }

    private void mergeSimilar(ArrayList<ActionStep> base) {
        int lastIndex = -1;
        for (int i = base.size() - 1; i >= 0; i--) {
            String name = base.get(i).name;
            int index = ORDER.get(name);

            if (index == lastIndex && canMerge(name)) {
                base.set(i, mergeActions(base.get(i), base.remove(i + 1)));
            } else {
                lastIndex = index;
            }

        }
    }

    private void orderCanonically(ArrayList<ActionStep> base) {
        Collections.sort(base, new Comparator<ActionStep>() {
            public int compare(ActionStep o1, ActionStep o2) {
                return ORDER.get(o1.name) - ORDER.get(o2.name);
            }
        });
    }

    private boolean parametersMatch(Param[] a, Param[] b) {
        return Arrays.equals(a, b);
    }

    private ArrayList<ActionStep> process(List<ActionStep> subList) {
        ArrayList<ActionStep> base = new ArrayList<ActionStep>(subList);
        dropAllExceptLast(base, "element", true);
        dropAllExceptLast(base, "diagram", true);
        dropAllExceptLast(base, "data", false);
        dropAllExceptLastByName(base, "data");
        dropAllExceptLastByName(base, "axes");
        dropAllExceptLastByName(base, "legends");
        dropAllExceptLastByName(base, "at");
        orderCanonically(base);
        mergeSimilar(base);

        Set<Object> usedParameters = accumulateNonDataParameters(base);
        dropDataParametersNotUsedElsewhere(base, usedParameters);

        return base;
    }

    static {
        ORDER = new HashMap<String, Integer>();
        for (String s : ParseGrammar.getCommands())
            ORDER.put(s, ORDER.size());
    }

}
