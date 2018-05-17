package org.brunel.data.auto;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.util.DateUnit;

/**
 * Information on a numeric span within a domain; sufficient to create a scale based off it
 */
public class NumericExtentDetail {
	public final double low;                   // Numeric low value
	public final double high;                  // Numeric high value
	public final DateUnit dateUnit;            // Is this a (numeric) date range -- if so, this is the unit for it
	public final boolean includeZeroDesired;   // if true, we really want to include zero in this span
	public final String transform;             // Desired transform name
	final double granularity;                  // Dispersion between data
	final boolean preferCategorical;           // True if, despite being numeric info, we'd prefer to be categorical
	final int optimalBinCount;                 // Preferred number of bins

	private NumericExtentDetail(double low, double high, DateUnit dateUnit, boolean includeZeroDesired,
								String transform, double granularity, boolean preferCategorical, int optimalBinCount) {
		this.low = low;
		this.high = high;
		this.dateUnit = dateUnit;
		this.includeZeroDesired = includeZeroDesired;
		this.transform = transform;
		this.granularity = granularity;
		this.preferCategorical = preferCategorical;
		this.optimalBinCount = optimalBinCount;
	}

	public static NumericExtentDetail makeForSimpleRange(double low, double high, DateUnit dateUnit, boolean includeZeroDesired) {
		return new NumericExtentDetail(low, high, dateUnit, includeZeroDesired, "linear", 0, false, 10);
	}

	/**
	 * Make a numeric span info on a field
	 * Uses "Auto" to fill in needed information
	 *
	 * @param f field to be based on
	 * @return info to extract from it
	 */
	public static NumericExtentDetail makeForField(Field f) {
		// Must bne numeric with a defined range (this guards against data with no values in it)
		if (f.isNumeric() && f.min() != null) {
			boolean needZero = f.name.equals("#count") || "sum".equals(f.strProperty("summary"));
			return new NumericExtentDetail(f.min(), f.max(), (DateUnit) f.property("dateUnit"),
					needZero, Auto.defineTransform(f),
					f.numProperty("granularity"), f.preferCategorical(), Auto.optimalBinCount(f));
		} else {
			return null;
		}
	}

	public double range() {
		return high - low;
	}

	NumericExtentDetail merge(NumericExtentDetail o) {
		// Other must be non-null, and we must only merge dates with dates
		if (o == null || dateUnit != o.dateUnit) return null;

		// This uses the coincidence that the lengths are sorted by need: log << root << linear
		String t = o.transform.length() < transform.length() ? transform : o.transform;
		return new NumericExtentDetail(Math.min(low, o.low),
				Math.max(high, o.high), dateUnit, includeZeroDesired || o.includeZeroDesired, t,
				Math.min(granularity, o.granularity), preferCategorical && o.preferCategorical,
				Math.max(optimalBinCount, o.optimalBinCount));
	}

	Object[] content() {
		return dateUnit == null ? new Object[]{low, high}
				: new Object[]{Data.asDate(low), Data.asDate(high)};

	}
}
