package org.brunel.data.auto;

import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines a domain on data
 * A domain is a set of values that will be used by a scale.
 * The domain can handle a mix of numeric, date and time domains
 */
public class Domain {

	private final List<DomainSpan> raw;                    // Added one at a time, without processing
	private final boolean preferContinuous;                // If true, we prefer continuous output
	private List<DomainSpan> merged;                       // When called, merges compatible domains
	private double[][] mergedRanges;                       // Fraction space occupied by spans within [0,1]

	/**
	 * Creates the domain with an empty list of spans.
	 * Some domains can be either continuous or categorical (e.g binned data) and so we bias the
	 * domain to say which it prefers to be when we create it.
	 *
	 * @param preferContinuous bias for type.
	 */
	public Domain(boolean preferContinuous) {
		this.preferContinuous = preferContinuous;
		this.raw = new ArrayList<>();
	}

	/**
	 * Add the field into the domain
	 * @param f field to add
	 * @return this
	 */
	public Domain include(Field f) {
		raw.add(DomainSpan.make(f, raw.size(), preferContinuous));    // Add it in (indexing to preserve sort order)
		merged = null;                                                // invalidate merging
		return this;
	}

	/**
	 * Get the space on the [0,1] unit that we should allocate to a given span
	 * @param index the span to get the range for
	 * @return span extent
	 */
	public double[] range(int index) {
		make();
		return mergedRanges[index];
	}

	/**
	 * Count the built spans
	 * @return spans
	 */
	public int spanCount() {
		return make().size();
	}

	/**
	 * Get the span at the given position.
	 * This is called after the raw spans have possibly been merged, so it is possible (indeed, likely)
	 * that there are fewer resutl spans than input ones
	 * @param index position to get span for
	 * @return span
	 */
	public DomainSpan span(int index) {
		return make().get(index);
	}

	// Process the raw fields into a minimal ordered list
	private List<DomainSpan> make() {
		if (merged == null) {
			makeDomains();
			makeRanges();
		}
		return merged;
	}

	private void makeDomains() {
		Collections.sort(raw);                                        // Sort into order
		merged = new ArrayList<>();
		DomainSpan current = null;                                // Try to merge into this
		for (DomainSpan span : raw) {
			if (current == null)
				current = span;                                        // The first span
			else {
				DomainSpan next = current.merge(span);               // Merge this with the current span
				if (next == null) {                                // Could not merge, so:
					merged.add(current);                            // Add the old one in
					current = span;                                    // This span is the new one to attempt to merge to
				} else {
					current = next;                                    // This is a successful merge
				}
			}
		}
		if (current != null) merged.add(current);                // Add the current span
	}

	private void makeRanges() {
		mergedRanges = new double[merged.size()][];
		double sizeTotal = 0.1 * (merged.size() - 1);                    // Start with the gaps, of size 0.1
		for (DomainSpan span : merged) sizeTotal += span.relativeSize();
		double at = 0.0;
		for (int i = 0; i < mergedRanges.length; i++) {
			double size = merged.get(i).relativeSize();
			mergedRanges[i] = new double[]{at / sizeTotal, (at + size) / sizeTotal};
			at += size + 0.1;

		}
	}
}
