/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.data;

import org.brunel.data.auto.Auto;
import org.brunel.data.io.Serialize;
import org.brunel.data.modify.AddConstantFields;
import org.brunel.data.modify.ConvertSeries;
import org.brunel.data.modify.DataOperation;
import org.brunel.data.modify.Filter;
import org.brunel.data.modify.Sort;
import org.brunel.data.modify.Stack;
import org.brunel.data.modify.Summarize;
import org.brunel.data.modify.Transform;
import org.brunel.data.util.Informative;
import org.brunel.data.values.ColumnProvider;
import org.brunel.translator.JSTranslation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Dataset extends Informative implements Serializable {

    /**
     * Make a dataset from rows of data, the first row are field names
     * Required for JavaScript
     *
     * @param rows input data
     * @return result data set, autoconverted
     */
    public static Dataset makeFromRows(Object[][] rows) {
        Field[] fields = new Field[rows[0].length];
        for (int j = 0; j < fields.length; j++) {
            Object[] column = new Object[rows.length - 1];
            for (int i = 1; i < rows.length; i++) column[i - 1] = rows[i][j];
            fields[j] = new Field(rows[0][j].toString(), null, new ColumnProvider(column));
        }
        return make(fields);
    }

    @JSTranslation(ignore = true)
    public static Dataset make(Field[] fields) {
        return make(fields, null);
    }

    /*
   * Make a data set from raw fields; we will add a "#count" and "#row" field.
   * By default we will automatically convert fields to their best unit
   */
    public static Dataset make(Field[] fields, Boolean autoConvert) {
        fields = ensureUniqueNames(fields);
        Field[] augmented = new Field[fields.length + 3];
        for (int i = 0; i < fields.length; i++)
            augmented[i] = Boolean.FALSE.equals(autoConvert) ? fields[i] : Auto.convert(fields[i]);
        int len = fields[0].rowCount();
        augmented[fields.length] = Data.makeConstantField("#count", "Count", 1.0, len);
        augmented[fields.length + 1] = Data.makeIndexingField("#row", "Row", len);

        // The selection data
        Field selection = Data.makeConstantField("#selection", "Selection", "\u2717", len);

        augmented[fields.length + 2] = selection;
        return new Dataset(augmented);
    }

    private static Field[] ensureUniqueNames(Field[] fields) {
        Set<String> cannotUse = new HashSet<String>();
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

    private Dataset(Field[] fields) {
        this.fields = ensureUniqueNames(fields);
        this.fieldByName = new HashMap<String, Field>();
        for (Field f : fields) fieldByName.put(f.name.toLowerCase(), f);
        for (Field f : fields) fieldByName.put(f.name, f);
    }

    /**
     * Create a new data set based on this one, with the designated fields binned
     *
     * @param command the fields to bin, semi-colon separated
     * @return binned data set
     */
    public Dataset bin(String command) {
        return Transform.transform(this, command);
    }

    /**
     * Remove the special fields form this data set -- useful when serializing
     */
    public Dataset removeSpecialFields() {
        List<Field> removed = new ArrayList<Field>();
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

    /**
     * Create a new data set based on this one, with some rows filtered out
     * <p/>
     * Commands are one of the following:
     * <p/>
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

    public Dataset replaceFields(Field[] fields) {
        Dataset result = new Dataset(fields);
        result.copyPropertiesFrom(this);
        return result;
    }

    public int rowCount() {
        return fields.length == 0 ? 0 : fields[0].rowCount();
    }

    public String name() {
        return stringProperty("name");
    }

    /**
     * Keeps only the indicated fields in the data set
     *
     * @param command names of the fields to keep
     * @return reduced data set
     */
    public Dataset reduce(String command) {
        Set<String> names = new HashSet<String>();
        Collections.addAll(names, DataOperation.parts(command));
        // keep special and used fields
        List<Field> ff = new ArrayList<Field>();
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
     * Create a new data set based on this one, but applying a sort
     * <p/>
     * The sort is based on the fields indicated, and is applied both to the rows and to the categories
     * of each categorical fields
     *
     * @param command the fields to sort by. This is an ordered list, with the first as the primary sort key
     * @return sorted data set
     */
    public Dataset sort(String command) {
        return Sort.transform(this, command);
    }

    /**
     * Returns a new data set which applies stacking to the data.
     * <p/>
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
     * <p/>
     * Each command generates a new field, using onr of the following syntax statements:<br/>
     * OUTPUT_FIELD = INPUT_FIELD                   -- define a field as group (measure)<br/>
     * OUTPUT_FIELD = INPUT_FIELD : base            -- define a field as group (measure) and use as base for percent<br/>
     * OUTPUT_FIELD = INPUT_FIELD : ????            -- define a field as a calculated value (measure)<br/>
     * <p/>
     * The calculation transforms allowed include the following: count, sum, mean, min, max, median, mode, q1, q3, iqr,
     * valid, list, range
     *
     * @param command each command defines a field in the output
     * @return sorted data set
     */
    public Dataset summarize(String command) {
        Dataset dataset = Summarize.transform(this, command);
        dataset.set("reduced", true); // Data has been reduced to only needed fields
        return dataset;
    }


    @JSTranslation(ignore = true)
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.write(Serialize.serializeDataset(this));
    }

    @JSTranslation(ignore = true)
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream store = new ByteArrayOutputStream();
        byte[] block = new byte[10240];
        for(;;) {
            int len = in.read(block);
            if (len <0) break;
            store.write(block, 0, len);
        }
        Dataset d = (Dataset) Serialize.deserialize(store.toByteArray());
        fields = d.fields;
        fieldByName = d.fieldByName;
        copyPropertiesFrom(d);
    }



}
