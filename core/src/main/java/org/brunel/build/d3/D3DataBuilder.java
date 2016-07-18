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

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.d3.D3Util.DateBuilder;
import org.brunel.build.data.DataTransformParameters;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.BuilderOptions.DataMethod;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;
import org.brunel.model.VisTypes.Interaction;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Write the Javascript for the data
 */
public class D3DataBuilder {

    public static void writeTables(VisItem main, ScriptWriter out, BuilderOptions options) {
        if (options.includeData == DataMethod.none) return;
        if (options.includeData == DataMethod.minimal) {
            throw new UnsupportedOperationException("Cannot make minimal data yet");
        }

        out.titleComment("Data Tables");

        NumberFormat format = new DecimalFormat();
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(8);

        Dataset[] datasets = main.getDataSets();
        for (int d = 0; d < datasets.length; d++) {
            Dataset data = datasets[d];
            Field[] fields;

            if (options.includeData == DataMethod.columns) {
                // Only the fields needed by the vis items
                LinkedHashSet<Field> fieldsAsSet = new LinkedHashSet<>();
                addUsedFields(main, data, fieldsAsSet);
                fields = fieldsAsSet.toArray(new Field[fieldsAsSet.size()]);
            } else {
                // All the fields
                fields = data.fields;
            }

            if (fields.length == 0) {
                // A Chart that doesn't actually use the data ... just meta values
                fields = new Field[]{Fields.makeConstantField("_dummy_", "Dummy", 1.0, data.rowCount())};
            }

            // Name the table with a numeric suffix for multiple tables
            out.onNewLine().add("var", String.format(options.dataName, d + 1), "= {").indentMore();

            out.onNewLine().add(" names: [");
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].isSynthetic()) continue;
                String name = fields[i].name;
                if (i > 0) out.add(", ");
                out.add("'").add(name).add("'");
            }
            out.add("], ");

            out.onNewLine().add(" options: [");
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].isSynthetic()) continue;
                String name;
                if (fields[i].isDate())
                    name = "date";
                else if (fields[i].isProperty("list"))
                    name = "list";
                else if (fields[i].isNumeric())
                    name = "numeric";
                else
                    name = "string";
                if (i > 0) out.add(", ");
                out.add("'").add(name).add("'");
            }
            out.add("], ");

            out.onNewLine().add(" rows: [");

            for (int r = 0; r < data.rowCount(); r++) {
                if (r > 0) out.add(",");
                String rowText = makeRowText(fields, r, format);
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

    private static void addUsedFields(VisItem item, Dataset data, Collection<Field> fields) {
        if (item.children() == null) {
            VisSingle vis = (VisSingle) item;                           // No children => VisSingle
            if (vis.getDataset() != data) return;                       // Does not use this data set, so ignore it
            for (String f : vis.usedFields(true))                       // Yes! Add in the fields to be used
                if (!f.startsWith("#")) {                               // .. but not synthetic fields
                    Field field = data.field(f, true);                  // Constant fields will not be found
                    if (field != null) fields.add(field);
                }
        } else {
            for (VisItem i : item.children())                           // Pass down to child items
                addUsedFields(i, data, fields);
        }
    }

    // If the row contains any nulls, return null for the whole row
    private static String makeRowText(Field[] fields, int r, NumberFormat format) {
        StringBuilder row = new StringBuilder();
        DateBuilder dateBuilder = new DateBuilder();
        row.append("[");
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (field.name.startsWith("#")) continue;           // Skip special fields
            if (i > 0) row.append(", ");
            Object value = field.value(r);
            if (value == null) {
                row.append("null");
            } else if (value instanceof Range) {
                row.append(Data.quote(value.toString()));
            } else if (field.isDate()) {
                Date date = Data.asDate(value);
                if (date == null) row.append("null");
                else row.append(dateBuilder.make(date, (DateFormat) field.property("dateFormat"), false));
            } else if (field.isNumeric()) {
                Double d = Data.asNumeric(value);
                if (d == null) row.append("null");
                else row.append(format.format(d));
            } else
                row.append(Data.quote(value.toString()));
        }
        row.append("]");
        return row.toString();
    }

    private final VisSingle vis;
    private final ScriptWriter out;
    private final Dataset data;
    private final int datasetIndex;

    public D3DataBuilder(VisSingle vis, ScriptWriter out, Dataset data, int index) {
        this.vis = vis;
        this.out = out;
        this.data = data;
        datasetIndex = index;
    }

    public void writeDataManipulation(Map<String, Integer> requiredFields) {
        out.onNewLine().ln().add("function makeData() {").ln().indentMore();

        // Guides do not use data, just the fields around it
        if (vis.tGuides.isEmpty()) {
            writeDataTransforms();
            writeHookup(requiredFields);
        }
        out.indentLess().onNewLine().add("}").ln();
    }

    private void writeDataTransforms() {
        // The parameters are stored in the data set when it is transformed
        DataTransformParameters params = (DataTransformParameters) data.property("parameters");
        D3Util.addTiming("Data Start", out);

        out.add("original = datasets[" + datasetIndex + "]").endStatement();
        out.add("if (filterRows) original = original.retainRows(filterRows)").endStatement();
        out.add("processed = pre(original,", datasetIndex, ")");
        out.mark();
        writeTransform("addConstants", params.constantsCommand);

        // Check for selection filtering
        Param param = vis.tInteraction.get(Interaction.filter);
        if (param != null) {
            if ("unselected".equals(param.asString()))
                writeTransform("filter", "#selection is " + Field.VAL_UNSELECTED);
            else
                writeTransform("filter", "#selection is " + Field.VAL_SELECTED);
        }

        writeTransform("each", params.eachCommand);
        writeTransform("transform", params.transformCommand);
        writeTransform("summarize", params.summaryCommand);
        writeTransform("filter", params.filterCommand);

        // Because series creates duplicates of fields, it is an expensive transformation
        // So we do not want to make it work on all fields, only the fields that are necessary.
        // So we reduce the data set to only necessary fields (summarize already does this, so
        // this step is not needed if summarize has been performed)
        if (!params.seriesCommand.isEmpty()) {
            if (params.summaryCommand.isEmpty()) writeTransform("reduce", params.usedCommand);
            writeTransform("series", params.seriesCommand);
        }
        writeTransform("sort", params.sortCommand);

        writeTransform("stack", params.stackCommand);               // Stack must come after all else

        if (vis.tDiagram == Diagram.network && vis.fY.size() > 1) {
            // We are using the 'Y' values to generate a set of identifier
            // We need to ensure the values are set in the summary, as well as any aesthetics
            String command = "#values=#values";
            for (String s : vis.aestheticFields()) if (!s.equals("#values")) command += ";" + s + "=" + s;
            writeTransform("summarize", command);
        }

        writeTransform("sortRows", params.sortRowsCommand);

        out.endStatement();
        out.add("processed = post(processed,", datasetIndex, ")").endStatement();

        D3Util.addTiming("Data End", out);
    }

    private void writeHookup(Map<String, Integer> fieldsToIndex) {

        // Get the list of fields we need as an array
        String[] fields = new String[fieldsToIndex.size()];
        for (Entry<String, Integer> e : fieldsToIndex.entrySet()) {
            fields[e.getValue()] = e.getKey();
        }

        out.onNewLine().add("var ");

        // Create references to the base fields
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.onNewLine();
            else out.indentMore();
            out.add("f" + i, "= processed.field(" + out.quote(fields[i]) + ")");
            if (i == fields.length - 1) out.endStatement();
            else out.add(",");
        }
        out.indentLess();

        // Define the key function
        out.add("var keyFunc = ");
        defineKeyFieldFunction(makeKeyFields(), false, fieldsToIndex);
        out.endStatement();

        out.add("data = {").ln().indentMore();

        // Add field definitions
        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            String fieldID = D3Util.canonicalFieldName(fields[fieldIndex]);
            out.add(fieldID, ":").at(24).add("function(d) { return f" + fieldIndex + ".value(d.row) },").ln();
        }

        // Add formatted field definitions
        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            String fieldID = D3Util.canonicalFieldName(fields[fieldIndex]);
            out.add(fieldID + "_f", ":").at(24).add("function(d) { return f" + fieldIndex + ".valueFormatted(d.row) },").ln();
        }
        // Add special items
        out.add("_split:").at(24);
        defineKeyFieldFunction(makeSplitFields(), true, fieldsToIndex);
        out.add(",").ln();
        out.add("_key:").at(24).add("keyFunc").add(",").ln();
        out.add("_rows:").at(24).add("BrunelD3.makeRowsWithKeys(keyFunc, processed.rowCount())");

        if (vis.fKeys.size() == 1 && vis.fX.size() == 1 && vis.fY.size() == 1) {
            out.add(",").ln();
            String id = "f" + fieldsToIndex.get(vis.fKeys.get(0).asField());
            String x = "f" + fieldsToIndex.get(vis.fX.get(0).asField());
            String y = "f" + fieldsToIndex.get(vis.fY.get(0).asField());
            out.add("_idToPoint:").at(24).add("BrunelD3.locate(" + id, ", ", x, ",", y, ", processed.rowCount())");
        }

        out.onNewLine().indentLess().add("}").endStatement();
    }

    private void writeTransform(String name, String command) {
        // Ignore if nothing to write
        if (!command.isEmpty())
            out.addChained(name, "(" + out.quote(command) + ")");
    }

    private void defineKeyFieldFunction(List<String> fields, boolean actsOnRowObject, Map<String, Integer> usedFields) {
        // Add the split fields accessor
        out.add("function(d) { return ");
        if (fields.isEmpty()) {
            out.add("'ALL'");
        } else {
            for (int i = 0; i < fields.size(); i++) {
                String s = fields.get(i);
                if (i > 0) out.add("+ '|' + ");
                out.add("f" + usedFields.get(s));
                out.add(actsOnRowObject ? ".value(d.row)" : ".value(d)");
            }
        }
        out.add(" }");
    }

    /**
     * The keys are used so that transitions when the data changes are logically consistent.
     * We want the right things to morph into each color as the data changes, and not be
     * dependent on table order. The following code works out the most likely items to be the keys
     * based on the type of chart being produced
     *
     * @return list of keys
     */
    public List<String> makeKeyFields() {
        // If we have defined keys, util them
        if (!vis.fKeys.isEmpty()) return asFields(vis.fKeys);

        if (vis.tDiagram == Diagram.chord) {
            List<String> result = new ArrayList<>();
            Collections.addAll(result, vis.positionFields());
            Collections.addAll(result, vis.aestheticFields());
            if (suitableForKey(result)) return result;
        }

        // Positions are the keys  treemaps
        if (vis.tDiagram == Diagram.map) {
            // Otherwise just use the position fields as usual
            return Arrays.asList(vis.positionFields());
        }

        if (vis.tDiagram == Diagram.network) {
            // The following handles the case when we use the edges as y values to make a key field for the nodes
            if (vis.fY.size() > 1) return Collections.singletonList("#values");
        }

        // If we split by aesthetics, they are the keys
        if (vis.tElement.producesSingleShape) return makeSplitFields();

        // For non-diagrams,
        if (vis.tDiagram == null) {
            // Always want the X fields
            List<String> result = asFields(vis.fX);

            // Y fields only if categorical
            for (Param p : vis.fY) {
                Field field = data.field(p.asField());
                if (field != null && field.preferCategorical()) result.add(p.asField());
            }

            // We only want to include the aesthetics when we they are stacked
            if (vis.stacked)
                Collections.addAll(result, vis.aestheticFields());
            if (suitableForKey(result)) return result;
        }

        // Default is the row values
        List<String> result = new ArrayList<>();
        result.add("#row");

        // Multiple rows have the same "#row" when we do make series
        if (vis.fY.size() > 1) result.add("#series");

        // Multiple rows have the same "#row" when we split up a list field using "each"
        for (Entry<Param, String> e : vis.fTransform.entrySet()) {
            if (e.getValue().equals("each")) result.add(e.getKey().asField());
        }

        return result;
    }

    private List<String> makeSplitFields() {
        // Start with all the aesthetics
        ArrayList<String> splitters = new ArrayList<>();

        // Always add splits and color
        for (Param p : vis.fSplits) splitters.add(p.asField());
        for (Param p : vis.fColor) splitters.add(p.asField());
        for (Param p : vis.fOpacity) splitters.add(p.asField());

        // We handle sized areas specially -- don't split using the size for them
        if (vis.tElement != Element.line && vis.tElement != Element.path) {
            for (Param p : vis.fSize) splitters.add(p.asField());
        }

        return splitters;
    }

    private List<String> asFields(List<Param> items) {
        List<String> fields = new ArrayList<>();
        for (Param p : items) fields.add(p.asField());
        return fields;
    }

    private boolean suitableForKey(List<String> result) {
        if (result.isEmpty()) return false;
        Field[] fields = new Field[result.size()];
        for (int i = 0; i < fields.length; i++) fields[i] = data.field(result.get(i));
        // Sort and see if any adjacent 'keys' are the same
        FieldRowComparison rowComparison = new FieldRowComparison(fields, null, false);
        int[] order = rowComparison.makeSortedOrder();
        for (int i = 1; i < order.length; i++)
            if (rowComparison.compare(order[i], order[i - 1]) == 0) return false;
        return true;
    }

}
