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
	/*
		This is a sorted list of data spans; each of which covers part of the data range
	 */
	private final List<DomainSpan> spans;

	/**
	 * Creates the domain with an empty list of spans
	 */
	public Domain() {
		this.spans = new ArrayList<>();
	}

	public void include(Field f) {
		// Make a domain span for the field
		DomainSpan d = f.preferCategorical() ? DomainSpan.makeCategorical(f)
				: DomainSpan.makeNumeric(f);

		// If we can add to an existing span, do so
		for (DomainSpan span : spans)
			if (span.include(d)) return;

		// Otherwise we have a new span
		spans.add(d);
	}

	// Get the domain contents to use
	public Object[][] domains() {
		Collections.sort(spans);                            // Sort into order
		Object[][] result = new Object[spans.size()][];
		for (int i = 0; i < result.length; i++)
			result[i] = spans.get(i).content();
		return result;
	}

	// Allocate the spans to ranges in [0,1]
	public double[][] domainRanges() {
		Collections.sort(spans);                            // Sort into order
		double[][] results = new double[spans.size()][];
		double sizeTotal = 0.1 * (spans.size() - 1);                    // Start with the gaps, of size 0.1
		for (DomainSpan span : spans) sizeTotal += span.relativeSize();
		double at = 0.0;
		for (int i = 0; i < results.length; i++) {
			double size = spans.get(i).relativeSize();
			results[i] = new double[]{at / sizeTotal, (at + size) / sizeTotal};
			at += size + 0.1;

		}
		return results;
	}
}
