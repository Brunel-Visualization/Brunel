/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.data;

import org.brunel.data.auto.Auto;
import org.brunel.data.io.Serialize;
import org.brunel.data.modify.AddConstantFields;
import org.brunel.data.modify.ConvertSeries;
import org.brunel.data.modify.DataOperation;
import org.brunel.data.modify.Each;
import org.brunel.data.modify.Filter;
import org.brunel.data.modify.SetRowCount;
import org.brunel.data.modify.Sort;
import org.brunel.data.modify.Stack;
import org.brunel.data.modify.Summarize;
import org.brunel.data.modify.Transform;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Informative;
import org.brunel.data.util.ItemsList;
import org.brunel.data.util.Range;
import org.brunel.data.values.ColumnProvider;
import org.brunel.translator.JSTranslation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Dataset extends Informative implements Serializable {

	@JSTranslation(ignore = true)
	public static Dataset make(Field[] fields) {
		return make(fields, null);
	}

	public static Dataset makeTyped(String[] names, String[] options, Object[][] rows) {
		/*
				var col, field, i, opt, fields = [];
        for (i = 0; i < data.names.length; i++) {
            col = data.rows.map(function (x) {
                var v = x[i];
                if (v && v.constructor === Array) return BrunelData.util_Range.make(v[0], v[1]);
                return v
            });                               // Extract i'th item
            var name = data.names[i];
            field = new BrunelData.Field(name, null, new BrunelData.values_ColumnProvider(col));
            opt = data.options ? data.options[i] : "string";     // Apply type options
            // Synthe
            if (opt == 'synthetic') {
                if (name == '#row') opt = 'list';
                if (name == '#count') opt = 'numeric';
            }
            if (opt == 'numeric') field = BrunelData.Data.toNumeric(field);
            else if (opt == 'date') field = BrunelData.Data.toDate(field);
            else if (opt == 'list') field = BrunelData.Data.toList(field);
            fields.push(field);
        }
        return BrunelData.Dataset.make(fields, false);

		 */
		Field[] fields = new Field[names.length];

		for (int k = 0; k < fields.length; k++) {
			// Options is a base type (string,date,numeric)
			// If a date, the format is added afterwards (date-Year, date-HourMinSec)
			// If a range, the range is first (range-numeric, range-date-Year)
			String[] o = options[k].split("-");
			boolean range = o[0].equals("range");
			String type = range ? o[1] : o[0];
			DateFormat df = type.equals("date") ? DateFormat.valueOf(range ? o[2] : o[1]) : null;
			Object[] values = new Object[rows.length];
			for (int i = 0; i < values.length; i++) {
				Object x = rows[i][k];
				if (x == null) {
					values[i] = null;
				} else if (range) {
					Object[] v = (Object[]) x;
					if (df == null)
						values[i] = Range.makeNumeric(Data.asNumeric(v[0]), Data.asNumeric(v[1]), false);
					else
						values[i] = Range.makeDateNative(Data.asDate(v[0]), Data.asDate(v[1]), false, df);
				} else if (type.equals("numeric")) {
					values[i] = Data.asNumeric(x);
				} else if (type.equals("date")) {
					values[i] = Data.asDate(x);
				} else {
					values[i] = x.toString();
				}
			}
			fields[k] = new Field(names[k], null, new ColumnProvider(values));
			if (type.equals("numeric")) fields[k].setNumeric();
			if (type.equals("date")) {
				fields[k].set("date", true);
				fields[k].setNumeric();
			}

		}
		return Dataset.make(fields, false);
	}

	/*
   * Make a data set from raw fields; we will add a "#count" and "#row" field.
   * By default we will automatically convert fields to their best unit
   */
	public static Dataset make(Field[] fields, Boolean autoConvert) {
		fields = ensureUniqueNames(fields);
		List<Field> augmented = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (Field f : fields) {
			if (!Boolean.FALSE.equals(autoConvert)) f = Auto.convert(f);
			augmented.add(f);
			names.add(f.name);
		}

		// Add undefined synthetic fields
		int len = fields.length == 0 ? 0 : fields[0].rowCount();
		if (!names.contains("#count")) augmented.add(Fields.makeConstantField("#count", "Count", 1.0, len));
		if (!names.contains("#row")) augmented.add(Fields.makeIndexingField("#row", "Row", len));
		if (!names.contains("#selection"))
			augmented.add(Fields.makeConstantField("#selection", "Selection", "\u2717", len));

		Field[] ff = augmented.toArray(new Field[augmented.size()]);
		return new Dataset(ff, null);
	}

	private static Field[] ensureUniqueNames(Field[] fields) {
		Set<String> cannotUse = new HashSet<>();
		cannotUse.add("");  // Cannot use an empty name
		Field[] result = new Field[fields.length];
		for (int i = 0; i < fields.length; i++) {
			String name = fields[i].name;
			if (name == null) name = "";
			if (!cannotUse.contains(name))
				result[i] = fields[i];
			else
				for (int k = 1; k < fields.length + 1; k++)
					if (!cannotUse.contains(name + "_" + k)) {
						result[i] = fields[i].rename(name + "_" + k, name);
						break;
					}
			cannotUse.add(result[i].name);  // used once; cannot use again
		}
		return result;
	}

	public Field[] fields;
	private Map<String, Field> fieldByName;

	protected Dataset(Field[] fields, Informative properties) {
		this.fields = ensureUniqueNames(fields);
		this.fieldByName = new HashMap<>();
		for (Field f : fields) fieldByName.put(f.name.toLowerCase(), f);
		for (Field f : fields) fieldByName.put(f.name, f);
		if (properties != null) copyAllProperties(properties);            // Same properties
	}

	public Dataset retainRows(int[] keep) {
		Field[] results = new Field[fields.length];
		for (int i = 0; i < results.length; i++)
			results[i] = Fields.permute(fields[i], keep, false);
		return replaceFields(results);
	}

	/**
	 * Create a new data set based on this one, with the designated fields binned
	 *
	 * @param command the fields to bin, semi-colon separated
	 * @return binned data set
	 */
	public Dataset transform(String command) {
		// Do not transform if already summarized
		return isSummarized() ? this : Transform.transform(this, command);
	}

	/**
	 * Remove the special fields form this data set -- useful when serializing
	 */
	public Dataset removeSpecialFields() {
		List<Field> removed = new ArrayList<>();
		for (Field f : fields) if (!f.name.startsWith("#")) removed.add(f);
		Field[] fields1 = removed.toArray(new Field[removed.size()]);

		return replaceFields(fields1);
	}

	/**
	 * Create a new data set based on this one, with the addition of some constant fields
	 * If the names are quoted, they are strings, otherwise they are numeric
	 *
	 * @param command the constants to add, semi-colon separated
	 * @return augmented data set
	 */
	public Dataset addConstants(String command) {
		return AddConstantFields.transform(this, command);
	}

	public long expectedSize() {
		long total = fields.length * 56 + 56;
		for (Field f : fields) total += f.expectedSize();
		return total;
	}

	@JSTranslation(ignore = true)
	public Field field(String name) {
		return field(name, false);
	}

	public Field field(String name, boolean lax) {
		Field field = fieldByName.get(name);
		return (field != null || !lax) ? field : fieldByName.get(name.toLowerCase());
	}

	public Field[] fieldArray(String[] names) {
		Field[] ff = new Field[names.length];
		for (int i = 0; i < ff.length; i++) ff[i] = field(names[i], false);
		return ff;
	}

	/**
	 * Create a new data set based on this one, with some rows filtered out
	 *
	 * Commands are one of the following:
	 *
	 * FIELD is a,b ...                -- one of those values                      [type 1]
	 * FIELD not a,b, ...              -- not one of those values                  [type 2]
	 * FIELD in a,b                    -- in that range of values (exactly two)    [type 3]
	 * FIELD valid                     -- not null                                 [type 4]
	 *
	 * @param command the fields to bin, separated by semi-colons
	 * @return filtered data set
	 */
	public Dataset filter(String command) {
		return Filter.transform(this, command);
	}

	/**
	 * Create a new data set based on this one, with multiple rows for each source row
	 * A list row will be split into the component pieces
	 *
	 * @param command the fields to 'each', separated by semi-colons
	 * @return increased data set
	 */
	public Dataset each(String command) {
		return Each.transform(this, command);
	}

	public Dataset replaceFields(Field[] fields) {
		return new Dataset(fields, this);
	}

	public int rowCount() {
		return fields.length == 0 ? 0 : fields[0].rowCount();
	}

	public String name() {
		return strProperty("name");
	}

	/**
	 * Keeps only the indicated fields in the data set
	 *
	 * @param command names of the fields to keep
	 * @return reduced data set
	 */
	public Dataset reduce(String command) {
		Set<String> names = new HashSet<>();
		Collections.addAll(names, DataOperation.strings(command, ';'));
		// keep special and used fields
		List<Field> ff = new ArrayList<>();
		for (Field f : this.fields) {
			if (f.name.startsWith("#") || names.contains(f.name))
				ff.add(f);
		}
		return replaceFields(ff.toArray(new Field[ff.size()]));
	}

	/**
	 * When there are multiple y fields, this transforms them to a single y field,
	 * with a new column (#series) giving which series they came from
	 *
	 * @param command the y fields to merge into a single #values field
	 * @return converted data set
	 */
	public Dataset series(String command) {
		return ConvertSeries.transform(this, command);
	}

	/**
	 * Replicate and/or merge rows until we have the desired number
	 *
	 * @param command the form is "field:count"
	 * @return converted data set
	 */
	public Dataset setRowCount(String command) {
		return SetRowCount.transform(this, command);
	}

	/**
	 * Create a new data set based on this one, but applying a sort
	 *
	 * The sort is based on the fields indicated, and is applied both to the rows and to the categories
	 * of each categorical fields
	 *
	 * @param command the fields to sort by. This is an ordered list, with the first as the primary sort key
	 * @return sorted data set
	 */
	public Dataset sort(String command) {
		return Sort.transform(this, command, true);
	}

	/**
	 * Create a new data set based on this one, but applying a sort
	 *
	 * The sort is based on the fields indicated, and is applied both the rows only
	 *
	 * @param command the fields to sort by. This is an ordered list, with the first as the primary sort key
	 * @return sorted data set
	 */
	public Dataset sortRows(String command) {
		return Sort.transform(this, command, false);
	}

	/**
	 * Returns a new data set which applies stacking to the data.
	 *
	 * Stacking is a complex operation which creates two new fields for the Y field, these are
	 * lower and upper values of the stacking. These are created by running through the data
	 * (sorting by x and aesthetics) so that all the Y values at an x location are 'stacked'.
	 * For these values they are given lower and upper values so that they form a stack, with the
	 * lower value of one row being the upper value of the immediately preceding row in the stack.
	 * If 'allCombinations' is set, then new values are added to the data to ensure that all combinations
	 * of aesthetics and x values exist. This is needed to draw stacked areas correctly, for example.
	 *
	 * @param command stacking parameters, as follows:
	 *                yField          the field to use for th Y dimension (must be non-null)
	 *                xFields         x fields (may be empty)
	 *                aestheticFields aesthetics (may be empty)
	 *                allCombinations whether to generate all possible x/aesthetic combinations
	 * @return stacked data set
	 */
	public Dataset stack(String command) {
		return Stack.transform(this, command);
	}

	/**
	 * Create a new data set based on this one by summarizing (aggregating) the data
	 *
	 * Each command generates a new field, using one of the following syntax statements:
	 * OUTPUT_FIELD = INPUT_FIELD                   -- define a field as group (measure)
	 * OUTPUT_FIELD = INPUT_FIELD : base            -- define a field as group (measure) and use as base for percent
	 * OUTPUT_FIELD = INPUT_FIELD : ????            -- define a field as a calculated value (measure)
	 *
	 * The calculation transforms allowed include the following: count, sum, mean, min, max, median, mode, q1, q3, iqr,
	 * valid, list, range
	 *
	 * @param command each command defines a field in the output
	 * @return sorted data set
	 */
	public Dataset summarize(String command) {
		// Do not summarize if already summarized
		return isSummarized() ? this : Summarize.transform(this, command);
	}

	private boolean isSummarized() {
		return Boolean.TRUE.equals(property("summarized"));
	}

	/**
	 * Modify the #selection field but applying the designated operation to the listed rows.
	 * It takes the current selection states and modifies them by applying the supplied method
	 * with the supplied rows. So, for example, "tog" toggles the selection status of the rows passed in
	 *
	 * Thsi method is called from JS to do selection
	 *
	 * @param method one of "add", "sub", "sel", "tog"
	 * @param row    the row from the source data to use in the operation
	 * @param source the Dataset in which we found the rows
	 * @param keys   the fields that identify which rows are the same (data keys)
	 */
	public void modifySelection(String method, Integer row, Dataset source, String[] keys) {
		String off = Field.VAL_UNSELECTED, on = Field.VAL_SELECTED;
		Field sel = field("#selection");
		int n = rowCount();

		// For simple selection (no modifiers) everything is initially cleared
		if (method.equals("sel"))
			for (int i = 0; i < n; i++) sel.setValue(off, i);

		Set<Integer> expanded = source.expandedOriginalRows(row, keys);
		for (int i : expanded) {
			switch (method) {
				case "sel":
				case "add":
					sel.setValue(on, i);
					break;
				case "sub":
					sel.setValue(off, i);
					break;
				default:
					sel.setValue(sel.value(i) == on ? off : on, i);
					break;
			}
		}

	}

	/**
	 * Expands this row to find similar rows, then returns all rows for the original data for those rows
	 * "Similar" means that they have the same values for the key fields
	 *
	 * @param row  target row to start with
	 * @param keys names of the fields to use as keys
	 * @return target rows (zero based)
	 */
	private Set<Integer> expandedOriginalRows(Integer row, String[] keys) {
		Set<Integer> expanded = new HashSet<>();
		if (row == null) return expanded;                                   // No data -- no rows

		int n = rowCount();

		// Get all the fields we want to use for comparison and build comparator
		Field[] keyFields = fieldArray(keys);
		FieldRowComparison compare = new FieldRowComparison(keyFields, null, false);

		Field rowField = field("#row");

		// Create a set of all rows similar to the target one
		for (int i = 0; i < n; i++)
			if (compare.compare(i, row) == 0) {
				Object o = rowField.value(i);
				if (o instanceof ItemsList) {
					ItemsList list = (ItemsList) o;
					for (int j = 0; j < list.size(); j++) expanded.add((Integer) list.get(j) - 1);
				} else if (o != null)
					expanded.add((Integer) o - 1);
			}
		return expanded;
	}

	@JSTranslation(ignore = true)
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.write(Serialize.serializeDataset(this));
	}

	@JSTranslation(ignore = true)
	private void readObject(ObjectInputStream in) throws IOException {
		ByteArrayOutputStream store = new ByteArrayOutputStream();
		byte[] block = new byte[10240];
		for (; ; ) {
			int len = in.read(block);
			if (len < 0) break;
			store.write(block, 0, len);
		}
		Dataset d = (Dataset) Serialize.deserialize(store.toByteArray());
		fields = d.fields;
		fieldByName = d.fieldByName;
		info = new HashMap<>();
		copyAllProperties(d);
	}

}
