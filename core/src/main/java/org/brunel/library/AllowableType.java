package org.brunel.library;

import org.brunel.data.Field;
import org.brunel.maps.GeoInformation;

import java.util.Arrays;

/**
 * Type for a library item field
 */
class AllowableType {

	static final AllowableType ANY = new AllowableType(false, false, false, null);
	static final AllowableType NUMERIC = new AllowableType(true, false, false, null);
	static final AllowableType CATEGORICAL = new AllowableType(false, true, false, null);
	static final AllowableType CATEGORICAL_FEW = new AllowableType(false, true, false, new int[]{2, 7});
	static final AllowableType CATEGORICAL_MEDIUM = new AllowableType(false, true, false, new int[]{3, 30});
	static final AllowableType CATEGORICAL_MANY = new AllowableType(false, true, false, new int[]{30, 1000});

	static final AllowableType MULTIPLE = new AllowableType(false, false, true, null);
	static final AllowableType MULTIPLE_CATEGORICAL = new AllowableType(false, true, true, new int[]{2, 30});

	static final AllowableType BINNABLE = new AllowableType(false, false, false, new int[]{3, 30}) {
		// We evaluate as if we are 'any', but then we bin afterwards
		public boolean requiresBinning() {
			return true;
		}
	};

	static final AllowableType TIME = new AllowableType(true, false, false, null) {
		// Match is not as good if field is not a time field
		public double match(Field f) {
			return super.match(f) * (f.isDate() ? 1.0 : 0.7);
		}
	};

	static final AllowableType NUMERIC_POSITIVE = new AllowableType(true, false, false, null) {
		// Match requires a min value >= 0
		public double match(Field f) {
			double v = super.match(f);
			return v > 0 && f.min() >= 0 ? v : 0.0;
		}
	};

	static final AllowableType GEO = new AllowableType(false, true, false, null) {
		// Special match uses only the geo names
		public double match(Field f) {
			if (f.isNumeric()) return 0.0;        // Must be purely categorical

			Double fraction = f.numProperty("fractionGeo");
			if (fraction == null) {
				fraction = GeoInformation.fractionGeoNames(f);
				f.set("fractionGeo", fraction);
			}
			return fraction * (f.uniqueValuesCount() > 3 ? 1 : 0.8);

		}
	};

	private final boolean requiredNumeric;
	private final boolean requiredCategorical;
	private final boolean multiple;
	private final int[] desiredCategories;

	public static AllowableType make(String s) {
		if (s.equals("any")) return ANY;
		if (s.equals("numeric")) return NUMERIC;
		if (s.equals("numeric-positive")) return NUMERIC_POSITIVE;
		if (s.equals("categorical")) return CATEGORICAL;
		if (s.equals("categorical-few")) return CATEGORICAL_FEW;
		if (s.equals("categorical-medium")) return CATEGORICAL_MEDIUM;
		if (s.equals("categorical-many")) return CATEGORICAL_MANY;
		if (s.equals("categorical-or-bin")) return BINNABLE;
		if (s.equals("multiple")) return MULTIPLE;
		if (s.equals("multiple-categorical")) return MULTIPLE_CATEGORICAL;
		if (s.equals("time")) return TIME;
		if (s.equals("geo")) return GEO;
		throw new IllegalStateException("Unknown type: " + s);
	}

	private AllowableType(boolean requiredNumeric, boolean requiredCategorical, boolean multiple, int[] desiredCategories) {
		this.requiredNumeric = requiredNumeric;
		this.requiredCategorical = requiredCategorical;
		this.multiple = multiple;
		this.desiredCategories = desiredCategories;
	}

	public boolean allowsMultiple() {
		return multiple;
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

	public boolean requiresBinning() {
		return requiredCategorical;
	}

	public String toString() {
		return "AllowableType{" + "requiredNumeric=" + requiredNumeric +
				", requiredCategorical=" + requiredCategorical +
				", multiple=" + multiple +
				", desiredCategories=" + Arrays.toString(desiredCategories) +
				'}';
	}
}
