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
	 * Build a domain from a numeric range
	 *
	 * @param f the field to use
	 * @return (numeric) domain span
	 */
	public static DomainSpan make(Field f, int index) {
		if (f.isNumeric())
			return new DomainSpan(f.preferCategorical() ? f.categories() : null, f.min(), f.max(), f.isDate(), index);
		else
			return new DomainSpan(f.categories(), Double.NaN, Double.NaN, false, index);
	}

	private final Object[] categories;       // Categorical data
	private final double low;                // Numeric low value
	private final double high;               // Numeric high value
	private final boolean isDate;            // Is this a (numeric) date range?
	private final int index;                // To preserve the order we added them in

	private DomainSpan(Object[] categories, double low, double high, boolean isDate, int index) {
		this.categories = categories;
		this.low = low;
		this.high = high;
		this.isDate = isDate;
		this.index = index;
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
		// Types comparison first
		int d = typeScore() - o.typeScore();
		if (d != 0) return d;

		// Order by low value, or nothing for non-numeric
		if (categories == null) return Data.compare(low, o.low);
		return index - o.index;
	}

	private int typeScore() {
		if (isDate) return categories == null ? 1 : 2;                // Dates first, with binned dates second
		if (!Double.isNaN(low)) return categories == null ? 3 : 4;    // Numeric next, with binned numerics after
		return 5;                                                    // Purely categorical is last
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
	 * @return the merged domain, or null if nothing could be done
	 */
	public DomainSpan merge(DomainSpan o) {
		if (o.isDate != isDate) return null;                    // Only match dates with dates

		Object[] mCats = mergeCategories(categories, o.categories);
		double a = Math.min(low, o.low);
		double b = Math.max(high, o.high);

		if (mCats == null && Double.isNaN(a)) return null;        // No match either for continuous or categories
		return new DomainSpan(mCats, a, b, isDate, Math.min(index, o.index));    // Matched result
	}

	private static Object[] mergeCategories(Object[] a, Object[] b) {
		if (a == null || b == null) return null;

		Set<Object> all = new HashSet<>();
		Collections.addAll(all, a);                                // Add all our categories to the set
		List<Object> ordered = new ArrayList<>();
		Collections.addAll(ordered, a);                            // Our categories go first
		for (Object o : b)                                        // Add new categories from the other list
			if (!all.contains(o)) ordered.add(o);
		return ordered.toArray(new Object[ordered.size()]);
	}

	public double relativeSize() {
		// Categories take up less space if there are fewer than 8 of them
		return categories == null ? 1.0 : Math.min(categories.length, 8) / 8.0;
	}
}
