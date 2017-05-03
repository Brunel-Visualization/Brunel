
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

package org.brunel.library;

import org.brunel.action.Action;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LibraryItem {

	private final String name;
	private final String description;
	private final String action;
	private final double desirability;                    // The base desirability, on a [0-1] scale
	private final AllowableType[] parameters;

	public LibraryItem(String name, String description, String action, double desirability, String[] parameters) {
		this.name = name;
		this.description = description;
		this.action = action;
		this.desirability = desirability;
		this.parameters = toAllowable(parameters);
	}

	public double suitability(Field... fields) {
		Field[][] mapping = mapFieldsToParameters(fields);                // Assign to best fits
		if (mapping == null) return 0.0;                                // Failed to map at all

		// Score the badness
		double score = desirability;
		for (int i = 0; i < parameters.length; i++) {
			for (Field f : mapping[i]) score *= parameters[i].match(f);
		}
		return score;
	}

	private Field[][] mapFieldsToParameters(Field[] fields) {

		int N = parameters.length;
		if (fields.length < N) return null;                                     // Need at least this many
		if (N == 0) return fields.length == 0 ? new Field[0][] : null;          // Degenerate case

		boolean allowsMultiple = parameters[N - 1].allowsMultiple();
		if (!allowsMultiple && fields.length > N) return null;                    // Too many fields

		// Find all the permutations of possible fields
		List<int[]> combinations = makeCombinations(fields.length, allowsMultiple ? N - 1 : N);

		int[] best = null;
		double bestScore = 1e-6;
		for (int[] combination : combinations) {
			double v = 1.0;
			for (int i = 0; i < combination.length; i++) {
				Field f = fields[combination[i]];
				v *= parameters[i].match(f);
				if (v <= bestScore) break;
			}

			if (allowsMultiple && v > bestScore) {
				for (Field f : unusedFields(fields, combination)) {
					v *= parameters[N - 1].match(f);
					if (v <= bestScore) break;
				}
			}

			if (v <= bestScore) continue;

			// Reduce score if things are out of order to bias to the original order
			// This means that for equally valid combinations, the one closer to original order is preferred
			for (int i = 1; i < combination.length; i++)
				if (combination[i] < combination[i-1]) v -= 0.001;

			if (v <= bestScore) continue;

			best = combination;
			bestScore = v;
		}

		if (best == null) return null;

		Field[][] results = new Field[N][];
		for (int i = 0; i < N; i++) {
			if (allowsMultiple && i == N - 1) {
				Set<Field> multiples = unusedFields(fields, best);
				results[i] = multiples.toArray(new Field[multiples.size()]);
			} else {
				results[i] = new Field[]{fields[best[i]]};
			}
		}

		return results;
	}

	private Set<Field> unusedFields(Field[] fields, int[] combination) {
		Set<Field> used = new LinkedHashSet<>(Arrays.asList(fields));
		try {
			for (int i : combination) used.remove(fields[i]);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return used;
	}

	static List<int[]> makeCombinations(int N, int C) {
		ArrayList<int[]> result = new ArrayList<>();

		boolean[] chosen = new boolean[N];                                // Already chosen
		int[] current = new int[C];
		addCombinationsRecursively(0, current, chosen, result);              // recurse, starting at position zero

		return result;
	}

	private static void addCombinationsRecursively(int pos, int[] current, boolean[] chosen, ArrayList<int[]> result) {
		if (pos == current.length) {
			// we have reached the end
			result.add(current.clone());
			return;
		}
		// Place items at position 'pos'
		for (int i = 0; i < chosen.length; i++) {
			if (!chosen[i]) {
				current[pos] = i;                                                // Choose this item
				chosen[i] = true;                                                // Mark as chosen
				addCombinationsRecursively(pos + 1, current, chosen, result);    // Recurse
				chosen[i] = false;                                                // unmark so it can be used again
			}
		}

	}

	private AllowableType[] toAllowable(String[] parameters) {
		AllowableType[] result = new AllowableType[parameters.length];
		for (int i = 0; i < result.length; i++) {
			String s = parameters[i].toLowerCase().trim();
			try {
				result[i] = AllowableType.make(s);
				if (result[i].allowsMultiple() && i != result.length - 1)
					throw new IllegalStateException("Multiples field must be the last parameter");
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Field type '" + s + " is not a valid type for a field");
			}
		}
		return result;

	}

	public LibraryAction apply(Field... fields) {
		return apply(fields, 1.0);
	}

	LibraryAction apply(Field[] targetFields, double score) {
		String text = action;
		List<Field> toBin = new ArrayList<>();

		Field[][] mapping = mapFieldsToParameters(targetFields);                // Assign to best fits

		// Step through the parameters in order of priority
		for (int i = 0; i < mapping.length; i++) {
			AllowableType parameter = parameters[i];
			if (parameter.allowsMultiple()) {
				Field[] fields = mapping[i];
				FieldCoarseness.sort(fields);
				text = modifyStringForMulti(text, fields, i);
				// Check for necessary binning
				if (parameter.requiresBinning())
					for (Field field : fields)
						if (!field.preferCategorical())
							toBin.add(field);
			} else {
				Field field = mapping[i][0];
				text = text.replaceAll("\\$" + (i + 1), field.name);
				// Check for necessary binning
				if (parameter.requiresBinning() && !field.preferCategorical())
					toBin.add(field);
			}
		}

		// Make and apply binning
		Action a = Action.parse(text);
		for (Field f : toBin)
			a = a.append(Action.parse("bin(" + f.name + ")"));
		return new LibraryAction(a.simplify(), name, description, score);
	}

	private String modifyStringForMulti(String text, Field[] fields, int index) {

		String all = "";

		for (int i = 0; i < fields.length; i++) {
			// Replace all the [] offsets, both from start and end
			text = text.replaceAll("\\$" + (index + 1) + "\\[" + i + "\\]", fields[i].name);
			text = text.replaceAll("\\$" + (index + 1) + "\\[-" + i + "\\]", fields[fields.length - 1 - i].name);

			// Add to 'all' string
			if (i > 0) all = all + ", ";
			all = all + fields[i].name;
		}

		// And replace any multis
		return text.replaceAll("\\$" + (index + 1), all);
	}

	public String toString() {
		return name;
	}
}
