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

package org.brunel.match;

import org.brunel.action.Action;
import org.brunel.action.ActionUtil;
import org.brunel.action.Param;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.model.VisItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Find the best matching action on a new data set given an action from an original data set.
 * @author drope
 *
 */
public class BestMatch {

	private static final int PARAMETER_CHOICE_MAX = 20;

	/**
	 * Find the best matching action
	 * @param originalData the original dataset
	 * @param newData the new dataset
	 * @param a the action applied to the original dataset
	 * @return a new action that is similar to 'a' but uses fields in the new data set
	 */
	public static Action match(Dataset originalData, Dataset newData, Action a) {
		List<BestActionParameterSet> parmSets = buildActionParameterSets(originalData, newData, a);
		List<ActionChoice> choices = buildActionChoices(a,parmSets);

		return choices.get(0).getAction();
	}

	/**
	 * Find the best matching action
	 * @param brunel brunel presumed to contain a data() statement
	 * @param newData the new data to use to do the match
	 * @return new Brunel
	 * @throws IOException if data cannot be properly retrieved
	 */

	public static Action match(String brunel, String newData) throws IOException {

		Action action = Action.parse(brunel);
		VisItem item = action.apply(null);
		if (item.getDataSets().length != 1) {
			throw new IllegalStateException("Data matching currently only supports Brunel syntax with a single data source. Could not match for Brunel: " + brunel);
		}
		Dataset originalDataset = item.getDataSets()[0];
		Dataset newDataset = DataCache.get(newData);
		Action a = match(originalDataset, newDataset, action);
		Param newDataParam = Param.makeString(newData);
		a = ActionUtil.replaceDataParameters(a, newDataParam);
		return a.simplify();

	}

	//Creates a set of choices for each action parameter
	private static ArrayList<BestActionParameterSet> buildActionParameterSets(Dataset originalData, Dataset newData, Action orignalAction) {

		Param[] parms = ActionUtil.parameters(orignalAction);
		ArrayList<BestActionParameterSet> choices = new ArrayList<BestActionParameterSet>(parms.length);

		for (int i =0; i < parms.length; i++) {
			choices.add(new BestActionParameterSet(originalData, newData, parms,i , PARAMETER_CHOICE_MAX));
		}

		return choices;

	}

	//Given a set of choices for each action parameter, creates a set of actions and scores for each one.
	private static ArrayList<ActionChoice> buildActionChoices(Action originalAction, List<BestActionParameterSet> parmSets ) {

		ArrayList<ActionChoice> choices = new ArrayList<ActionChoice>();

		while (maxSize(parmSets) > 1) {
			ActionParameterChoice[] parmChoice = new ActionParameterChoice[parmSets.size()];
			ArrayList<String> usedFields = new ArrayList<String>();

			for (int i=0; i< parmChoice.length; i++) {
				BestActionParameterSet parmSet = parmSets.get(i);
				parmChoice[i] = parmSet.pullNextActionParameterChoice(usedFields);
				int duelEncodedIndex = parmChoice[i].getDualEncodedLinkIndex();
				String field = parmChoice[i].getField();

				//Handle dual encoded action fields
				if (duelEncodedIndex >=0) {
					String f = parmChoice[duelEncodedIndex].getField();
					Param p = Param.makeField(f).addModifiers(parmChoice[i].getActionParameter().modifiers());
					parmChoice[i] = new ActionParameterChoice(p, parmChoice[i].getScore());
				}

				//Retain fields already used
				if (field != null ) {
					usedFields.add(field);
				}
			}
			choices.add(ActionChoice.makeActionChoice(originalAction, parmChoice));
		}
		Collections.sort(choices);

		return choices;
	}


	//Current maximum size of each action parameter set.
	private static int maxSize(List<BestActionParameterSet> parmSets ) {
		int size = 0;

		for (BestActionParameterSet p : parmSets) {
			size = Math.max(size, p.size());
		}

		return size;
	}

}
