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

package org.brunel.match;

import org.brunel.action.Action;
import org.brunel.action.ActionUtil;
import org.brunel.action.Param;

/**
 * Scoreable Action
 * @author drope
 *
 */
public class ActionChoice extends ScoredChoice {

	private final Action action;

	private ActionChoice(Action action, double score) {
		super(score);
		this.action = action;
	}

	public Action getAction() {
		return action;
	}

	/**
	 * Creates an ActionChoice by assembling provided action parameters and geo-meaning the scores to produce the
	 * overall score for the resulting Action.
	 * @param originalAction An originating action to replace the parameters
	 * @param chosenParms A set of action parameter choices to use for the new Action.
	 * @return
	 */
	public static ActionChoice makeActionChoice(Action originalAction, ActionParameterChoice[] chosenParms) {

		int size = chosenParms.length;
		double score = 1.0;
		Param[] parms = new Param[size];

		for (int i=0;i< parms.length; i++) {
			ActionParameterChoice c = chosenParms[i];
			parms[i] = c.getActionParameter();
			score *= c.getScore();
		}

		score = Math.pow(score, 1.0/(double)size);

		return new ActionChoice(ActionUtil.replaceParameters(originalAction, parms),score);
	}
}
