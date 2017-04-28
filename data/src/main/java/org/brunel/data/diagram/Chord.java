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

import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A chord diagram shows sized links between two categorical fields.
 * This class takes the data in standard format and converts it into a form
 * that can be used for ribbons
 */
public class Chord {

	// This is the fraction of the semi-circular arc for the group that we allocate to padding
	private static final double PAD = 0.1;

	public static Chord make(Dataset data, String fieldA, String fieldB, String fieldSize) {
		return new Chord(data, fieldA, fieldB, fieldSize);
	}

	public final ChordRibbon[] chords;
	public final ChordGroup[] groups;

	public Chord(Dataset data, String fieldA, String fieldB, String fieldSize) {

		Field fA = data.field(fieldA);
		Field fB = data.field(fieldB);

		// First, we make a hierarchical that nests all 'B' values within 'A' values, and use the 'A' values
		Hierarchical H = Hierarchical.makeByNestingFields(data, null, fieldSize, fieldA, fieldB);

		// Capture the group information (list and map of objects)
		List<ChordGroup> groupA = new ArrayList<>();
		List<ChordGroup> groupB = new ArrayList<>();
		Map<Object, ChordGroup> aGroups = new HashMap<>();
		Map<Object, ChordGroup> bGroup = new HashMap<>();

		// Capture the edges -- these will be the ribbon chords
		List<ChordRibbon> edges = new ArrayList<>();

		// The total sizes of all arcs (same as sums for each group)
		double totalSize = 0;

		// Step through 'A' nodes (top level children) and 'B' nodes (lower level children)
		for (Node a : (Node[]) H.root.children) {
			if (a.children == null) continue;			// This is when the field for 'a' is null
			for (Node b : (Node[]) a.children) {
				if (b.children == null) continue;		// This is when the field for 'b' is null
				for (Node c : (Node[]) b.children) {

					// 'a' and 'b' are the groups -- 'c' is a list of leaf nodes values withing the groups

					int row = c.row;
					double size = c.value;

					// Create or add to the size of the relevant groups
					ChordGroup gpA = addTo(groupA, aGroups, fA.value(row), size);
					ChordGroup gpB = addTo(groupB, bGroup, fB.value(row), size);

					// Add the new ribbon
					edges.add(new ChordRibbon(gpA.index, gpB.index, row, size));

					// Add to the total size
					totalSize += size;
				}
			}
		}

		// Divide up space for the chord groups: group A on the left, anti-clockwise; B on the right, clockwise
		layout(groupA, totalSize, 2 * Math.PI, Math.PI);
		layout(groupB, totalSize, 0, Math.PI);

		// Layout the chords
		layoutChords(edges, groupA, groupB, totalSize);

		// Merge the two groups together and store as groups
		groupA.addAll(groupB);
		groups = groupA.toArray(new ChordGroup[groupA.size()]);

		// Fix the groups so they are always oriented clockwise
		for (ChordGroup g : groups) {
			if (g.startAngle > g.endAngle) {
				double v = g.startAngle;
				g.startAngle = g.endAngle;
				g.endAngle = v;
			}
		}

		// Store the links
		chords = edges.toArray(new ChordRibbon[edges.size()]);
	}

	private void layoutChords(List<ChordRibbon> edges, List<ChordGroup> A, List<ChordGroup> B, double totalSize) {
		double totalAngle = Math.PI * (1.0 - PAD);                // Space for all group values

		// Thee are running totals for angles within each group
		double[] angleA = new double[A.size()];
		double[] angleB = new double[B.size()];

		for (ChordRibbon e : edges) {
			int ai = e.source.index;
			int bi = e.target.index;
			ChordGroup a = A.get(ai);
			ChordGroup b = B.get(bi);
			double angularSpan = totalAngle * e.size / totalSize;          	// Divide total angle space up
			e.source.endAngle = a.startAngle + angleA[ai];                	// To make sure angle\es are clockwise, start with the end
			e.source.startAngle = e.source.endAngle - angularSpan;
			e.target.startAngle = b.startAngle + angleB[bi];                // Start of target
			e.target.endAngle = e.target.startAngle + angularSpan;          // End of source
			angleA[ai] -= angularSpan;										// increment start (anti-clockwise)
			angleB[bi] += angularSpan;										// increment start
		}
	}

	private void layout(List<ChordGroup> group, double totalSize, double startAngle, double endAngle) {
		double totalAngle = (endAngle - startAngle) * (1.0 - PAD);                // Space for all group values
		double padBetween = (endAngle - startAngle) * PAD / group.size();        // Amount to add between groups
		double rStart = startAngle + padBetween / 2;                            // Start by padding half the amount

		for (ChordGroup g : group) {
			g.startAngle = rStart;
			g.endAngle = rStart + totalAngle * g.value / totalSize;                // Divide space up by sizes
			rStart = g.endAngle + padBetween;                                    // Add padding for next group item
		}
	}

	private ChordGroup addTo(List<ChordGroup> list, Map<Object, ChordGroup> nameMap, Object value, double size) {
		ChordGroup g = nameMap.get(value);
		if (g == null) {
			// Create a new group and add to the list
			g = new ChordGroup(list.size(), value);
			nameMap.put(value, g);
			list.add(g);
		}
		// Increment the size
		g.value += size;
		return g;
	}

}


