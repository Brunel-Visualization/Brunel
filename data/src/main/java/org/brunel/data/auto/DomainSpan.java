package org.brunel.data.auto;

import org.brunel.data.Data;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A single span of domains
 */
public class DomainSpan implements Comparable<DomainSpan> {
	/**
	 * Build a domain from categories
	 *
	 * @param f the field to use
	 * @return (categorical) domain span
	 */
	public static DomainSpan makeCategorical(Field f) {
		return new DomainSpan(f.categories(), 0, 0, false);
	}

	/**
	 * Build a domain from a numeric range
	 *
	 * @param f the field to use
	 * @return (numeric) domain span
	 */
	public static DomainSpan makeNumeric(Field f) {
		return new DomainSpan(null, f.min(), f.max(), f.isDate());
	}

	private Object[] categories;       // Categorical data
	private double low;                // Numeric low value
	private double high;               // Numeric high value
	private boolean isDate;            // Is this a (numeric) date range?

	private DomainSpan(Object[] categories, double low, double high, boolean isDate) {
		this.categories = categories;
		this.low = low;
		this.high = high;
		this.isDate = isDate;
	}

	/**
	 * Sorting
	 * Base order is date, numeric, categorical
	 * Within numerics, sort by min values. Numerics sort by size f categories
	 *
	 * @param o other to compare to
	 * @return sort order
	 */
	public int compareTo(DomainSpan o) {
		if (categories != null) {
			// we are categorical
			return o.categories == null ? 1 :
					o.categories.length - categories.length;
		} else {
			if (o.categories != null) return -1;                // numerics go first
			if (isDate != o.isDate) return isDate ? -1 : 1;     // dates go ahead of non-dates
			return Double.compare(low, o.low);                  // By lowest min value
		}
	}

	public Object[] content() {
		if (categories != null) return categories;
		if (isDate) return new Object[]{Data.asDate(low), Data.asDate(high)};
		return new Object[]{low, high};
	}

	/**
	 * Attempt to update this domain also to include another domain
	 *
	 * @param o the other domain to include
	 * @return false if it could not be done
	 */
	public boolean include(DomainSpan o) {
		if (categories != null) {
			if (o.categories == null)
				return false;            // Only categorical domains can merge with categorical
			Set<Object> all = new HashSet<>();
			Collections.addAll(all, categories);                // Add all our categories to the set

			List<Object> ordered = new ArrayList<>();
			Collections.addAll(ordered, categories);            // Our categories go first
			for (Object a : o.categories)                    // Add new categories from the other list
				if (!all.contains(a)) ordered.add(a);
			if (ordered.size() > categories.length)                // Only update if we need to
				categories = ordered.toArray(new Object[ordered.size()]);
		} else {
			if (o.categories != null) return false;          // Only numeric domains can merge with numeric
			if (o.isDate != isDate) return false;            // Only match dates with dates
			this.low = Math.min(low, o.low);
			this.high = Math.max(high, o.high);
		}
		// we updated
		return true;
	}

	public double relativeSize() {
		// Categories take up less space if there are fewer than 8 of them
		return categories == null ? 1.0 : Math.min(categories.length, 8) / 8.0;
	}
}
