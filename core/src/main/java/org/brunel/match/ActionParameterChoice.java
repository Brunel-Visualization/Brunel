/*
 * Copyright (c) 2016 IBM Corporation and others.
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

import org.brunel.action.Param;

/**
 * Allows a score to be assigned to a given ActionParameter.
 * @author drope
 *
 */

class ActionParameterChoice extends ScoredChoice {

	private final Param param;
	private final int dualEncodedLinkIndex;

	public ActionParameterChoice(Param param, double score) {
		super(score);
		this.param = param;
		this.dualEncodedLinkIndex = -1;
	}

	/**
	 * Use when the ActionParameter is for a field that is dual encoded.  The index is the location
	 * in an array of ActionParameter for the original.
	 * @param dualEncodedLinkIndex not sure ...
	 */
	public ActionParameterChoice(int dualEncodedLinkIndex, Param originalParm) {
		super(1.0);
		param = originalParm;
		this.dualEncodedLinkIndex = dualEncodedLinkIndex;
	}

	public Param getActionParameter() {
		return param;
	}

	public String getField() {
		if (param == null) return null;
		if (param.isField()) return param.asField();
		return null;
	}

	public int getDualEncodedLinkIndex() {
		return dualEncodedLinkIndex;
	}



}
