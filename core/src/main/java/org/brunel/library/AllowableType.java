package org.brunel.library;

import org.brunel.data.Field;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Type for a library item field
 */
class AllowableType {

	private static final AllowableType ANY = new AllowableType(false, false, false, null);
	private static final AllowableType NUMERIC = new AllowableType(true, false, false, null);
	private static final AllowableType CATEGORICAL = new AllowableType(false, true, false, null);
	private static final AllowableType CATEGORICAL_FEW = new AllowableType(false, true, false, new int[]{2, 7});
	private static final AllowableType CATEGORICAL_MANY = new AllowableType(false, true, false, new int[]{40, 1000});

	private final boolean requiredNumeric;
	private final boolean requiredCategorical;
	private final boolean multiple;
	private final int[] desiredCategories;

	public static AllowableType make(String s) {
		if (s.equals("any")) return ANY;
		if (s.equals("numeric")) return NUMERIC;
		if (s.equals("categorical")) return CATEGORICAL;
		if (s.equals("categorical-few")) return CATEGORICAL_FEW;
		if (s.equals("categorical-many")) return CATEGORICAL_MANY;
		throw new IllegalStateException("Unknown type: " + s);
	}

	AllowableType(boolean requiredNumeric, boolean requiredCategorical, boolean multiple, int[] desiredCategories) {
		this.requiredNumeric = requiredNumeric;
		this.requiredCategorical = requiredCategorical;
		this.multiple = multiple;
		this.desiredCategories = desiredCategories;
	}

	/**
	 * A high value means it accepts few possibilities
	 *
	 * @return value used to sort into strictness of fields allowed
	 */
	private int strictness() {
		int score = multiple ? 0 : 1000;        // multiples are always last
		if (requiredNumeric) score += 100;        // very important
		if (requiredCategorical) score += 10;    // significant
		if (desiredCategories != null) score += 5;    // added strictness
		return score;
	}

	public boolean allowsMultiple() {
		return multiple;
	}

	/**
	 * Return the order in which to process -- we want strict ones first
	 *
	 * @param types types to sort
	 * @return processing order
	 */
	public static Integer[] makeMatchingOrder(final AllowableType[] types) {
		Integer[] order = new Integer[types.length];
		for (int i = 0; i < order.length; i++) order[i] = i;

		Arrays.sort(order, new Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				// MOST strict first. If tied, preserve order
				int diff = types[b].strictness() - types[a].strictness();
				return diff != 0 ? diff : a - b;
			}
		});

		return order;
	}

	public double match(Field f) {
		if (requiredNumeric && !f.isNumeric()) return 0.0;              // Numeric is needed

		double value = 1.0;                                             // base value
		if (requiredNumeric && f.preferCategorical()) value *= 0.9;     // not wanted, but possible
		if (requiredCategorical && f.isNumeric()) value *= 0.8;         // have to bin the data

		if (desiredCategories != null) {
			double cats = f.preferCategorical() ? f.categories().length : 9;
			if (cats > desiredCategories[1])                            // Too many
				value *= Math.sqrt(desiredCategories[1] / cats);
			if (cats < desiredCategories[0])                            // Too few
				value *= Math.sqrt(cats / desiredCategories[0]);
		}

		return value;
	}

	public boolean requiresCategorical() {
		return requiredCategorical;
	}

	public boolean requiresNumeric() {
		return requiredNumeric;
	}

}
