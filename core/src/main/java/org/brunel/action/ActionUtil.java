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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A class for manipulating actions and getting information about them
 */
public class ActionUtil {

    /**
     * Assembles an array of all parameters used by an action.
     * It concatenates all the parameters for each step together to form the array
     *
     * @param action the action to analyze
     * @return non-null array of all parameters
     */
    public static Param[] parameters(Action action) {
        List<Param> parameters = new ArrayList<Param>();
        for (ActionStep step : action.steps)
            Collections.addAll(parameters, step.parameters);
        return parameters.toArray(new Param[parameters.size()]);
    }

    /**
     * Creates a new action by using the same commands as in the original one, but on
     * a new set of parameters. Note that no checking is performed to make sure the action
     * makes sense or the array is the right length. In general a good pattern is to use the results
     * of <code>ActionUtil.parameters(...)</code> to get the initial parameters and modify that.
     *
     * @param action     action to modify
     * @param parameters new parameters for it to use.
     * @return resulting action
     */
    public static Action replaceParameters(Action action, Param[] parameters) {
        ActionStep[] replacementSteps = new ActionStep[action.steps.length];
        int at = 0;
        for (int i = 0; i < action.steps.length; i++) {
            Param[] singleParams = new Param[action.steps[i].parameters.length];
            for (int j = 0; j < singleParams.length; j++)
                singleParams[j] = parameters[at++];
            replacementSteps[i] = replaceParameters(action.steps[i], singleParams);
        }
        return new Action(replacementSteps);
    }

    /**
     * Creates a new action step by using the same command as in the original one, but on
     * a new set of parameters. Note that no checking is performed to make sure the parameters make sense.
     * Also, this method may return the original action if the parameters are unchanged
     *
     * @param step       action step to modify
     * @param parameters new parameters for it to use.
     * @return resulting action step
     */
    public static ActionStep replaceParameters(ActionStep step, Param[] parameters) {
        return Arrays.equals(step.parameters, parameters) ? step
                : new ActionStep(step.name, parameters);
    }
}
