/*
 * Copyright (c) 2016 IBM Corporation and others.
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

import org.brunel.data.values.ColumnProvider;
import org.brunel.data.values.ConstantProvider;
import org.brunel.data.values.ReorderedProvider;
import org.brunel.data.values.RowProvider;

/**
 * Utilities for manipulating fields
 */
public class Fields {

    /**
     * Define a constant field
     *
     * @param name  field name
     * @param label user-readable label for the field
     * @param o     constant value
     * @param len   field length
     * @return constructed field
     */
    public static Field makeConstantField(String name, String label, Object o, int len) {
        Field field = new Field(name, label, new ConstantProvider(o, len));
        if (Data.asNumeric(o) != null) field.setNumeric();
        return field;
    }

    /**
     * Define a field that is a simple 1-based indexing
     *
     * @param name  field name
     * @param label user-readable label for the field
     * @param len   field length
     * @return constructed field
     */
    public static Field makeIndexingField(String name, String label, int len) {
        Field field = new Field(name, label, new RowProvider(len));
        field.setNumeric();
        return field;
    }

    /**
     * Define a field for a column of data
     *
     * @param name  field name
     * @param label user-readable label for the field
     * @param data  data to be used -- must all be of same type
     * @return constructed field
     */
    public static Field makeColumnField(String name, String label, Object[] data) {
        return new Field(name, label, new ColumnProvider(data));
    }

    /**
     * Create a new field that indexes into the original field
     *
     * @param field            field to permute
     * @param order            the new order
     * @param onlyOrderChanged true if this is a true permutation (no duplicates or any left out)
     * @return constructed field
     */
    public static Field permute(Field field, int[] order, boolean onlyOrderChanged) {
        if (field.provider instanceof ConstantProvider) {
            // No ned for hard work here -- a permuted constant is still a constant
            if (onlyOrderChanged) return field;
            else return makeConstantField(field.name, field.label, field.value(0), field.rowCount());
        }
        if (onlyOrderChanged)
            return new Field(field.name, field.label, new ReorderedProvider(field.provider, order), field);
        Field f = new Field(field.name, field.label, new ReorderedProvider(field.provider, order));
        copyBaseProperties(field, f);
        return f;
    }

    /**
     * Copy properties from one field to another
     * @param source source field
     * @param target target field
     */
    public static void copyBaseProperties(Field source, Field target) {
        target.copyProperties(source, "numeric", "binned", "summary", "transform",
                "list", "listCategories", "date", "categoriesOrdered", "dateUnit", "dateFormat");

        // Only copy the categories if the order is important
        if (source.propertyTrue("categoriesOrdered"))
            target.set("categories", source.property("categories"));
    }
}
