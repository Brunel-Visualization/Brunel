
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
import java.util.Collection;
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
		Field[][] results = new Field[parameters.length][];

		Set<Field> remaining = new LinkedHashSet<>(Arrays.asList(fields));
		Integer[] order = AllowableType.makeMatchingOrder(parameters);
		for (int i : order) {
			if (remaining.isEmpty()) return null;            // Run out of fields
			AllowableType target = parameters[i];
			if (target.allowsMultiple()) {
				if (target.requiresNumeric()) {
					// Check the remaining fields are all numeric
					for (Field field : remaining) if (!field.isNumeric()) return null;
				}
				results[i] = remaining.toArray(new Field[remaining.size()]);            // Use them all
				remaining.clear();
			} else {
				// Find the bets one to fit
				if (!setBestSingleMatch(results, remaining, i, parameters[i])) return null;
			}
		}

		// If we have used up all the fields, we are good!
		return remaining.isEmpty() ? results : null;
	}

	// returns true if a match was set
	private boolean setBestSingleMatch(Field[][] results, Set<Field> remaining, int i, AllowableType type) {
		Field best = findBestFit(type, remaining);
		if (best == null) return false;
		results[i] = new Field[]{best};
		remaining.remove(best);
		return true;
	}

	private Field findBestFit(AllowableType type, Collection<Field> fields) {
		Field best = null;
		double bestMatch = 0;
		for (Field field : fields) {
			double m = type.match(field);
			if (m > bestMatch) {
				best = field;
				bestMatch = m;
			}
		}
		return best;
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

	LibraryAction apply(Field[] fields, double score) {
		String text = action;
		List<Field> toBin = new ArrayList<>();

		// Step through the parameters
		for (int i = 0; i < parameters.length; i++) {
			AllowableType parameter = parameters[i];
			if (parameter.allowsMultiple()) {
				// Modify text
				text = modifyStringForMulti(text, fields, i);
				// Check for necessary binning
				for (int j = i; j < fields.length; j++)
					if (parameter.requiresCategorical() && !fields[j].preferCategorical())
						toBin.add(fields[j]);
			} else {
				// Modify text
				text = text.replaceAll("\\$" + (i + 1), fields[i].name);
				// Check for necessary binning
				if (parameter.requiresCategorical() && !fields[i].preferCategorical())
					toBin.add(fields[i]);
			}
		}

		// Make and apply binning
		Action a = Action.parse(text);
		for (Field f : toBin)
			a = a.append(Action.parse("bin(" + f.name + ")"));
		return new LibraryAction(a.simplify(), name, description, score);
	}

	private String modifyStringForMulti(String text, Field[] fields, int start) {
		int n = fields.length - start;

		String all = "";

		for (int i = 0; i < n; i++) {
			// Replace all the [] offsets, both from start and end
			text = text.replaceAll("\\$" + (start + 1) + "\\[" + i + "\\]", fields[start + i].name);
			text = text.replaceAll("\\$" + (start + 1) + "\\[-" + i + "\\]", fields[fields.length - 1 - i].name);

			// Add to 'all' string
			if (i > 0) all = all + ", ";
			all = all + fields[start + i].name;
		}

		// And replace any multis
		return text.replaceAll("\\$" + (start + 1), all);
	}

	public String toString() {
		return name;
	}
}
