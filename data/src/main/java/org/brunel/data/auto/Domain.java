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

	private final List<DomainSpan> raw;                   	// Added one at a time
	private final boolean preferContinuous;					// If true, we prefer continuous output
	private List<DomainSpan> merged;                        // When called, merges compatible domains

	/**
	 * Creates the domain with an empty list of spans
	 *
	 * @param preferContinuous
	 */
	public Domain(boolean preferContinuous) {
		this.preferContinuous = preferContinuous;
		this.raw = new ArrayList<>();
	}

	public Domain include(Field f) {
		raw.add(DomainSpan.make(f, raw.size(), preferContinuous));  	// Add it in (indexing to preserve sort order)
		merged = null;                                        			// invalidate merging
		return this;
	}

	public int spanCount() {
		return merge().size();
	}

	public DomainSpan getSpan(int index) {
		return merge().get(index);
	}

	public boolean isEmpty() {
		return merge().isEmpty();
	}

	/**
	 * Return true if the span is numeric (this includes dates)
	 *
	 * @param index span index to check
	 * @return true if it is a numeric (which includes date) span
	 */
	public boolean isNumeric(int index) {
		DomainSpan span = getSpan(index);
		return span.categories == null || span.numeric != null && preferContinuous;
	}

	/**
	 * Return true if the span is categorical
	 *
	 * @param index span index to check
	 * @return true if it is a categorical  span
	 */
	public boolean isCategorical(int index) {
		return !isNumeric(index);
	}

	// Process the raw fields into a minimal ordered list
	private List<DomainSpan> merge() {
		if (merged == null) {
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
		return merged;
	}

	// Allocate the spans to ranges in [0,1]
	public double[][] domainRanges() {
		merge();
		double[][] results = new double[merged.size()][];
		double sizeTotal = 0.1 * (merged.size() - 1);                    // Start with the gaps, of size 0.1
		for (DomainSpan span : merged) sizeTotal += span.relativeSize();
		double at = 0.0;
		for (int i = 0; i < results.length; i++) {
			double size = merged.get(i).relativeSize();
			results[i] = new double[]{at / sizeTotal, (at + size) / sizeTotal};
			at += size + 0.1;

		}
		return results;
	}
}
