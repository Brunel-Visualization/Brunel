package org.brunel.build.data;

import org.brunel.build.info.ElementStructure;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimizes the data needed to be passed down
 */
public class DataMinimizer {
	private final Collection<Field> required;
	private final Dataset original;
	private final Set<ElementStructure> elements;

	public DataMinimizer(Collection<Field> required, Dataset original, Set<ElementStructure> elements) {
		this.required = required;
		this.original = original;
		this.elements = elements;
	}

	/**
	 * Minimize the data sets, returning null if we could not do so
	 *
	 * @return minimized fields if possible, otherwise null
	 */
	public Collection<Field> getMinimized() {

		// Find all the elements that match this data
		List<ElementStructure> structures = new ArrayList<>();
		for (ElementStructure e : elements)
			if (e.data.getSource() == original) structures.add(e);

		// Basic check to see if they are candidates for being minimized
		for (ElementStructure structure : structures) {
			TransformParameters p = structure.data.getTransformParameters();

			// There is no point trying to minimize; without a summarization we need all the data anyway
			if (p.summaryCommand.isEmpty()) return null;
		}

		// Create initial reduced set of parameters
		TransformParameters params = makeReducedTransformParams(structures.get(0).data.getTransformParameters());

		// Run through the rest and make sure they are compatible
		for (int i = 1; i < structures.size(); i++) {
			 params = merge(params, structures.get(i).data.getTransformParameters());
		}

		// Failure to merge
		if (params == null) return null;

		// OK, we are good -- use the transforms
		Dataset transformed = TransformedData.transform(original, params);
		return matchRequiredFields(transformed);
	}

	private Collection<Field> matchRequiredFields(Dataset dataset) {
		List<Field> fields = new ArrayList<>();
		for (Field field : required) {
			Field matched = dataset.field(field.name);
			if (matched == null)
				throw new IllegalArgumentException("Unmatched field: " + field.name);
			fields.add(matched);
		}
		return fields;
	}

	/**
	 * Attempt to combine two transforms into a "minimal" combined transformation
	 */
	public TransformParameters merge(TransformParameters a, TransformParameters b) {
		if (a == null || b == null) return null;	// Once it goes wrong, it stays wrong


		// Adding constants is easy -- we can add as many as we like
		a.constantsCommand = mergeSemiColonSeparatedLists(a.constantsCommand, b.constantsCommand);

		// Eliminating the use of "each" is safe -- it means that when we summarize using
		// that field, we use the combined list as a key, which is just less efficient
		a.eachCommand = eliminateIfDifferent(a.eachCommand, b.eachCommand);

		// The transforms (bins, ranks) must be the same, otherwise summaries will be all wrong
		if (!a.transformCommand.equals(b.transformCommand)) return null;

		// The summaries must also match
		if (!a.summaryCommand.equals(b.summaryCommand)) return null;

		return a;
	}

	private TransformParameters makeReducedTransformParams(TransformParameters a) {
		TransformParameters result = new TransformParameters();
		result.constantsCommand = a.constantsCommand;
		result.eachCommand = a.eachCommand;
		result.transformCommand = a.transformCommand;
		result.summaryCommand = a.summaryCommand;

		result.rowCountCommand = "";
		result.seriesCommand = "";
		result.filterCommand = "";
		result.sortCommand = "";
		result.sortRowsCommand = "";
		result.stackCommand = "";

		return result;
	}

	// If different, return the empty command
	private String eliminateIfDifferent(String a, String b) {
		return a.equals(b) ? a : "";
	}

	// Merge lists of the form "a; b;d"
	private String mergeSemiColonSeparatedLists(String a, String b) {
		if (a.equals(b)) return a;
		if (a.isEmpty()) return b;
		if (b.isEmpty()) return a;
		Set<String> all = new LinkedHashSet<>();
		Collections.addAll(all, a.split("; *"));
		Collections.addAll(all, b.split("; *"));
		return Data.join(all, "; ");
	}

}
