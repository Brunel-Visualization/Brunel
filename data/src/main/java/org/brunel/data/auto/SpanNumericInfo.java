package org.brunel.data.auto;

import org.brunel.data.Data;

/**
 * Information on a numeric span within a domain; sufficient to create a scale based off it
 */
class SpanNumericInfo {
	final double low;                   // Numeric low value
	final double high;                  // Numeric high value
	final boolean isDate;               // Is this a (numeric) date range?
	final boolean includeZeroDesired;   // if true, we really want to include zero in this span

	SpanNumericInfo(double low, double high, boolean isDate, boolean includeZeroDesired) {
		this.low = low;
		this.high = high;
		this.isDate = isDate;
		this.includeZeroDesired = includeZeroDesired;
	}

	SpanNumericInfo merge(SpanNumericInfo o) {
		// Other must be non-null, and we must only merge dates with dates
		if (o == null || isDate != o.isDate) return null;
		return new SpanNumericInfo(Math.min(low, o.low),
				Math.max(high, o.high), isDate, includeZeroDesired || o.includeZeroDesired);
	}

	Object[] range() {
		return isDate
				? new Object[]{Data.asDate(low), Data.asDate(high)}
				: new Object[]{low, high};
	}
}
