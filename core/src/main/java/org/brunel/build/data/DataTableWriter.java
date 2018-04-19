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

package org.brunel.build.data;

import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.BuilderOptions.DataMethod;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;
import org.brunel.model.VisItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.brunel.data.util.DateFormat.YearMonthDay;

/**
 * Write the Javascript for the data
 */
public class DataTableWriter {

	private final VisItem main;
	private final Set<ElementStructure> elements;
	private final ScriptWriter out;
	private final BuilderOptions options;

	private final SimpleDateFormat dateFormatter, dateTimeFormatter;

	public DataTableWriter(VisItem main, Set<ElementStructure> elements, ScriptWriter out, BuilderOptions options) {
		this.main = main;
		this.elements = elements;
		this.out = out;
		this.options = options;
		dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
		dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		dateTimeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public void write() {
		DataMethod method = options.includeData;
		if (method == DataMethod.none) return;

		Dataset[] datasets = main.getDataSets();

		out.titleComment("Data Tables");

		for (int i = 0; i < datasets.length; i++) {
			Dataset dataset = datasets[i];

			Collection<Field> required;
			boolean summarized = false;
			if (method == DataMethod.full) {
				required = stripSynthetic(Arrays.asList(dataset.fields));
			} else if (method == DataMethod.columns) {
				required = stripSynthetic(findUsed(dataset));
			} else if (method == DataMethod.minimal) {
				required = findUsed(dataset);
				Collection<Field> minimal = new DataMinimizer(required, dataset, elements).getMinimized();
				if (minimal == null) {
					// Failed to minimize; just strip synthetic fields (treating like "columns")
					required = stripSynthetic(required);
				} else {
					// Success -- use it
					required = minimal;
					summarized = true;
				}
			} else {
				throw new IllegalStateException("Unknown method option: " + method);
			}

			// If a chart does not actually use any data, we need to add a dummy field
			if (required.isEmpty())
				required.add(Fields.makeConstantField("_dummy_", "Dummy", 1.0, dataset.rowCount()));

			writeTable(i, required, summarized);
		}
	}

	private void appendValue(StringBuilder row, Field field, Object value) {
		if (value == null) {
			row.append("null");
		} else if (value instanceof Range) {
			Range range = (Range) value;
			row.append('[');
			appendValue(row, field, range.low);
			row.append(',');
			appendValue(row, field, range.high);
			row.append(']');
		} else if (field.isDate()) {
			DateFormat df = (DateFormat) field.property("dateFormat");
			String d = df.ordinal() >= YearMonthDay.ordinal()
					? dateFormatter.format(Data.asDate(value))
					: dateTimeFormatter.format(Data.asDate(value));
			row.append(Data.quote(d));
		} else if (field.isNumeric()) {
			Double d = Data.asNumeric(value);
			if (d == null) row.append("null");
			else row.append(d.toString());
		} else
			row.append(Data.quote(value.toString()));
	}

	private Set<Field> findUsed(Dataset dataset) {
		Set<Field> result = new LinkedHashSet<>();
		for (ElementStructure e : elements) {
			// When the element uses this data source, add in all the fields used by it
			if (e.data.getSource() == dataset) {
				String[] fieldsUsed = e.vis.usedFields(true);
				for (String s : fieldsUsed) {
					Field field = dataset.field(s);
					if (field != null) result.add(field);                // Constant fields will not be found
				}
			}
		}
		return result;
	}

	// If the row contains any nulls, return null for the whole row
	private String makeRowText(Field[] fields, int r) {
		StringBuilder row = new StringBuilder();
		row.append("[");
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (i > 0) row.append(", ");
			Object value = field.value(r);
			appendValue(row, field, value);
		}
		row.append("]");
		return row.toString();
	}

	private List<Field> stripSynthetic(Collection<Field> fields) {
		List<Field> result = new ArrayList<>();
		for (Field field : fields)
			if (!field.isSynthetic()) result.add(field);
		return result;
	}

	private void writeTable(int index, Collection<Field> ff, boolean summarized) {
		Field[] fields = ff.toArray(new Field[ff.size()]);

		// Name the table with a numeric suffix for multiple tables
		out.onNewLine().add("var", String.format(options.dataName, index + 1), "= {").indentMore();
		out.onNewLine().add(" summarized: " + summarized + ",");

		out.onNewLine().add(" names: [");
		for (int i = 0; i < fields.length; i++) {
			String name = fields[i].name;
			if (i > 0) out.add(", ");
			out.add("'").add(name).add("'");
		}
		out.add("], ");

		out.onNewLine().add(" options: [");
		for (int i = 0; i < fields.length; i++) {
			String name;
			if (fields[i].isDate())
				name = "date-" + fields[i].property("dateFormat");
			else if (fields[i].isProperty("list"))
				name = "list";
			else if (fields[i].isNumeric())
				name = "numeric";
			else
				name = "string";

			// Tag up front for ranges
			if (fields[i].value(0) instanceof Range) {
				name = "range-" + name;
			}

			if (i > 0) out.add(", ");
			out.add("'").add(name).add("'");
		}
		out.add("], ");

		out.onNewLine().add(" rows: [");

		for (int r = 0; r < fields[0].rowCount(); r++) {
			if (r > 0) out.add(",");
			String rowText = makeRowText(fields, r);
			if (out.currentColumn() + rowText.length() > 99)
				out.onNewLine();
			else if (r > 0)
				out.add(" ");
			out.add(rowText);
		}
		out.add("]");
		out.indentLess().onNewLine().add("}").endStatement();
	}
}
