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

import org.brunel.data.Dataset;
import org.brunel.model.VisException;
import org.brunel.model.VisComposition;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An Action is an immutable object that contains a sequence of commands that can be used to build a visualization.
 * The usual lifecycle is to util <code>Action.parse(...)</code> to create an Action,
 * and then call <code>Action.apply(data)</code> to make a <code>VisItem</code> from a <code>DataSet</code>.
 * <p/>
 * An Action has hashcode, equals, and compare all defined and so can be used in collections freely.
 * The <code>toString()</code> version is in the same format as the input pares text, so you can util it as if it
 * were the input.
 * In fact the chain <code>String canonical = Action.parse(text).simplify().toString()</code> is an
 * easy way to parse and "regularize" an input text.
 */
public class Action implements Comparable<Action> {

    /**
     * Parses the text and returns an action for it; throws an error if syntactically incorrect
     *
     * @param text input text
     * @return valid action
     * @throws VisException wraps any error in parsing
     */
    public static Action parse(String text) {
        return Parser.parse(text);
    }

    /* The sequence of action commands */
    final ActionStep[] steps;

    /* Basic Constructor */
    Action(ActionStep... actions) {
        this.steps = actions;
    }

    /**
     * Append an action to this one
     *
     * @param action the action to append
     * @return a new action consisting of the steps form the first followed by the steps from the second
     */
    public Action append(Action action) {
        if (action == null) return this;
        ActionStep[] others = action.steps;
        ActionStep[] combined = new ActionStep[steps.length + others.length];
        System.arraycopy(steps, 0, combined, 0, steps.length);
        System.arraycopy(others, 0, combined, steps.length, others.length);
        return new Action(combined);
    }

    /**
     * Create a VisItem by applying this action.
     * The data is not defined externally, so must be defined in the action by a 'data' statement
     *
     * @return resulting visualization
     */
    public VisItem apply() {
        return apply(null);
    }

    /**
     * Create a VisItem by applying this action to a set of data
     *
     * @param data target data
     * @return resulting visualization
     */
    public VisItem apply(Dataset data) {
        List<ActionStep> all = Arrays.asList(this.steps);                       // All the steps
        List<List<ActionStep>> splits = split(all, "|");                        // Create a list per tiled chart
        if (splits == null) return applyChartElements(all, data);               // Shortcut for single list
        VisItem[] charts = new VisItem[splits.size()];                          // One chart per list
        for (int i = 0; i < charts.length; i++)                                 // For each chart ..
            charts[i] = applyChartElements(splits.get(i), data);                // .. build the chart elements
        return VisComposition.tile(charts);                                     // Tile if needed
    }

    /**
     * Actions are sorted by simplicity -- the simpler ones first.
     * Simpler means fewer steps. If the steps are the same, we compare parameters
     *
     * @param o other action to compare to
     * @return less than 0 for simpler, greater than 0 for more complex. Zero for equality
     */
    public int compareTo(Action o) {
        if (this == o) return 0;

        // Shorter items first
        if (steps.length != o.steps.length) {
            return steps.length - o.steps.length;
        }

        for (int i = 0; i < steps.length; i++) {
            ActionStep a = steps[i];
            ActionStep b = o.steps[i];
            int c = a.name.compareTo(b.name);
            if (c != 0) return c;
            if (a.parameters.length != b.parameters.length) return a.parameters.length - b.parameters.length;
            for (int j = 0; j < a.parameters.length; j++) {
                c = a.parameters[j].compareTo(b.parameters[j]);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    /**
     * Standard Hashcode
     *
     * @return standard hashcode
     */
    public int hashCode() {
        return 31 + Arrays.hashCode(steps);
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof Action)) return false;
        return Arrays.equals(steps, ((Action) other).steps);
    }

    /**
     * The string representation is suitable for re-using as input to a Parse
     *
     * @return valid string representation fo the action
     */
    public String toString() {
        final StringBuilder b = new StringBuilder();
        for (ActionStep a : steps) {
            if (b.length() > 0) b.append(" ");
            b.append(a.toString());
        }
        return b.toString();
    }

    /**
     * Simplifies an action by removing unused constructs and replaced commands
     * and converts it to a standard form if needed.
     *
     * @return simplified version
     */
    public Action simplify() {
        if (steps.length == 1) return this;
        ActionStep[] sequence = new ActionSimplification(steps).make();
        if (sequence.length == 0) return null;
        return new Action(sequence);
    }

    /* Creates a VisItem for a single chart */
    private VisItem applyChartElements(List<ActionStep> steps, Dataset data) {
        List<List<ActionStep>> splits = split(steps, "+");                              // Split list into elements
        if (splits == null) return applyNesting(steps, data);                           // Shortcut for a single element
        VisItem[] elements = new VisItem[splits.size()];                                // Multiple elements
        for (int i = 0; i < elements.length; i++)                                       // For each element
            elements[i] = applyNesting(splits.get(i), data);                            // ... make and nest if needed
        return VisComposition.overlay(elements);                                        // Return the composition
    }

    /* Apply nesting if needed */
    private VisItem applyNesting(List<ActionStep> steps, Dataset data) {
        // We work forwards, keeping track of what nesting we need to do
        VisSingle outer = new VisSingle(data);
        for (int i = 0; i < steps.size(); i++) {
            ActionStep a = steps.get(i);
            if (a.name.startsWith(">")) {
                // Create whatever is on the other side of the operator and return the composition
                VisItem inner = applyNesting(steps.subList(i + 1, steps.size()), data);
                return a.name.equals(">")
                        ? VisComposition.inside(outer, inner)
                        : VisComposition.nested(outer, inner);
            } else {
                // Keep modifying the outer part
                outer = a.apply(outer);
            }
        }

        // There was no nesting -- all we have is the outer part, so return it
        return outer;
    }

    /* Split a list of steps into sublists using the requested action name to split by */
    private List<List<ActionStep>> split(List<ActionStep> steps, String splitActionName) {
        List<List<ActionStep>> splits = new ArrayList<List<ActionStep>>();
        List<ActionStep> current = new ArrayList<ActionStep>();
        for (ActionStep a : steps) {
            if (a.name.equals(splitActionName)) {
                if (current.isEmpty())
                    throw new IllegalStateException("Error processing '" + splitActionName +
                            "'. Composition must join standard actions");
                splits.add(current);
                current = new ArrayList<ActionStep>();
            } else {
                current.add(a);
            }
        }

        // If there are no splits, return null to signal a simple case with no splits
        if (splits.isEmpty()) return null;

        if (current.isEmpty())
            throw new IllegalStateException("Error processing '" + splitActionName +
                    "'. Composition must join standard actions");
        splits.add(current);
        return splits;
    }

}
