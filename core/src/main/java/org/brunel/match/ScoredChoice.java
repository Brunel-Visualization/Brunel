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


/**
 * Simple base class for something that can be scored and sorted highest to lowest.
 * @author drope
 *
 */
class ScoredChoice implements Comparable<ScoredChoice> {

	private final double score;

	ScoredChoice(double score) {
		this.score = score;
	}

	public double getScore() {
		return score;
	}

	@Override
	public int compareTo(ScoredChoice o) {
		if (score > o.score) return -1;
		if (score == o.score) return 0;
		return 1;
	}
}
