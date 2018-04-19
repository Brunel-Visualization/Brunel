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

package org.brunel.data.diagram;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class TestChord {

	private static final String csv = Data.join(new String[]{
			"A,B,C,D",
			"a,x,1,4",
			"b,x,2,3",
			"c,y,1,2",
			"c,x,2,1",
			"c,y,5,1",
	}, "\n", true);

	private static final Dataset simple = Dataset.make(CSV.read(csv));

	@Test
	public void testGroups() {
		Chord data = new Chord(simple, "A", "B", null);
		ChordGroup[] groups = data.groups;

		// Should be 5 groups:
		assertEquals(5, groups.length);                // No aggregation; should be one link per item in the data

		// Anti-clockwise starting at the top
		assertEquals(325, r(groups[0].startAngle));
		assertEquals(357, r(groups[0].endAngle));

		assertEquals(286, r(groups[1].startAngle));
		assertEquals(319, r(groups[1].endAngle));

		assertEquals(183, r(groups[2].startAngle));
		assertEquals(280, r(groups[2].endAngle));

		// Clockwise starting at the top
		assertEquals(5, r(groups[3].startAngle));
		assertEquals(102, r(groups[3].endAngle));

		assertEquals(111, r(groups[4].startAngle));
		assertEquals(176, r(groups[4].endAngle));
	}

	@Test
	public void testEdges() {
		Chord data = new Chord(simple, "A", "B", null);
		ChordRibbon[] chords = data.chords;

		// Should be 5 chords: No aggregation; should be one link per item in the data

		// a -> x
		assertEquals(0, chords[0].source.index);
		assertEquals(0, chords[0].target.index);
		assertEquals(0, chords[0].row);

		// b -> x
		assertEquals(1, chords[1].source.index);
		assertEquals(0, chords[1].target.index);
		assertEquals(1, chords[1].row);

		// etc.
	}


	// Round radian to nearest degree
	private int r(double v) {
		return (int) Math.round(v * 360 / 2 / Math.PI);
	}

}
