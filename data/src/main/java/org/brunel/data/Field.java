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

import org.brunel.data.stats.DateStats;
import org.brunel.data.stats.NominalStats;
import org.brunel.data.stats.NumericStats;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Informative;
import org.brunel.data.util.Range;
import org.brunel.data.values.Provider;

import java.util.HashMap;

public class Field extends Informative implements Comparable<Field> {

    public static final String VAL_SELECTED = "\u2713";         // Value for selected item
    public static final String VAL_UNSELECTED = "\u2717";       // Value for unselected item

    public final String label;                  // human-readable, not necessarily unique
    public final String name;                   // unique within the data set
    Provider provider;                          // Provides values for the field (not final as it may need conversion)

    private boolean calculatedNominal, calculatedNumeric, calculatedDate;   // True when we calculate these
    private HashMap<Object, Integer> categoryOrder;                             // order of the categories

    public Field(String name, String label, Provider provider) {
        this(name, label, provider, null);
    }

    Field(String name, String label, Provider provider, Field base) {
        this.name = name;
        this.label = label == null ? name : label;
        this.provider = provider;

        // Information is provided in the base field
        if (base != null) {
            if (provider == null) {
                // Ensure that the base field has everything calculated because we cannot calculate lazily later
                base.makeNominalStats();
                base.makeNumericStats();
                base.makeDateStats();
            }
            copyPropertiesFrom(base);
        }
    }

    /**
     * Sets a value for a field.
     * This method is used by selection to set the selection results.
     * It should not be used by general programming, as fields may share data and so setting the value in one
     * field may affect other fields -- or cached data sets.
     * @param o value to set
     * @param index index at which to set the value
     */
    public void setValue(Object o, int index) {
        // We may have to convert a provider from a constant provider
        provider = provider.setValue(o, index);
    }

    public int compareRows(int a, int b) {
        if (categoryOrder == null) {
            categoryOrder = new HashMap<Object, Integer>();         // Build it no matter what so next call is faster
            if (preferCategorical()) {
                Object[] cats = categories();
                for (int i = 0; i < cats.length; i++) categoryOrder.put(cats[i], i);
            }
        }
        return provider.compareRows(a, b, categoryOrder);
    }

    public long expectedSize() {
        return (label.length() + name.length()) * 2 + 84 + 24 + provider.expectedSize();
    }

    public Object property(String key) {
        Object o = super.property(key);
        if (o == null) {
            if (!calculatedNominal && NominalStats.creates(key)) {
                makeNominalStats();
                o = super.property(key);
            }
            if (!calculatedNumeric && NumericStats.creates(key)) {
                if (!calculatedNominal) makeNominalStats();
                makeNumericStats();
                o = super.property(key);
            }
            if (!calculatedDate && DateStats.creates(key)) {
                if (!calculatedNominal) makeNominalStats();
                if (!calculatedNumeric) makeNumericStats();
                makeDateStats();
                o = super.property(key);
            }
        }
        return o;
    }

    public void setCategories(Object[] cats) {
        // We must make the nominal stats first to prevent them being overridden.
        makeNominalStats();
        set("categories", cats);
    }

    private void makeDateStats() {
        if (isNumeric()) DateStats.populate(this);
        calculatedDate = true;
    }

    public boolean isNumeric() {
        return Boolean.TRUE.equals(property("numeric"));
    }

    public boolean isDate() {
        return Boolean.TRUE.equals(property("date"));
    }

    public boolean isBinned() {
        return Boolean.TRUE.equals(property("binned"));
    }

    private void makeNumericStats() {
        if (provider != null) NumericStats.populate(this);
        calculatedNumeric = true;
    }

    private void makeNominalStats() {
        if (provider != null) NominalStats.populate(this);
        calculatedNominal = true;
    }

    public Object[] categories() {
        return (Object[]) property("categories");
    }

    public int compareTo(Field o) {
        int p = Data.compare(name, o.name);
        if (name.startsWith("#")) return o.name.startsWith("#") ? p : 1;
        return o.name.startsWith("#") ? -1 : p;
    }

    /**
     * Return a new field without the data included
     *
     * @return field with information, but no data
     */
    public Field dropData() {
        return new Field(name, label, null, this);
    }

    public boolean hasProvider() {
        return provider != null;
    }

    public Double max() {
        return (Double) property("max");
    }

    public Double min() {
        return (Double) property("min");
    }

    public boolean isSynthetic() {
        //Probably should use a property to indicate this instead
        return name.startsWith("#");
    }

    public boolean preferCategorical() {
        return !isNumeric() || Boolean.TRUE.equals(property("binned")) ;
    }

    public boolean ordered() {
        return isNumeric() || name.equals("#selection");
    }

    public Field rename(String name, String label) {
        Field field = new Field(name, label, provider);
        field.copyPropertiesFrom(this);
        return field;
    }

    public int rowCount() {
        return provider != null ? provider.count() : numericProperty("n").intValue();
    }

    public String toString() {
        return name;
    }

    public int uniqueValuesCount() {
        return (int) Math.round(numericProperty("unique"));
    }

    public int valid() {
        return (Integer) property("valid");
    }

    public Object value(int index) {
        return provider.value(index);
    }

    public String valueFormatted(int index) {
        return format(provider.value(index));
    }

    public String format(Object v) {
        if (v == null) return "?";
        if (v instanceof Range) return v.toString();
        if (isDate())
            return ((DateFormat) property("dateFormat")).format(Data.asDate(v));
        if ("percent".equals(property("summary"))) {
            // Show one decimal place at most
            Double d = Data.asNumeric(v);
            if (d == null) return null;
            return Data.formatNumeric(Math.round(d * 10) / 10.0, false) + "%";
        }
        if (isNumeric()) {
            Double d = Data.asNumeric(v);
            return d == null ? "?" : Data.formatNumeric(d, true);
        }
        return v.toString();
    }

}
