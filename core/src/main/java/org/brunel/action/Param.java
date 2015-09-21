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

package org.brunel.action;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encapsulate a parameter for an action.
 */
public class Param implements Comparable<Param> {

    public static Param makeString(String s) {
        return new Param(s, Type.string);
    }

    public static Param makeNumber(Number s) {
        return new Param(s, Type.number);
    }

    public static Param makeOption(String s) {
        return new Param(s, Type.option);
    }

    public static Param makeField(String s) {
        return new Param(s, Type.field);
    }

    public static List<Param> makeFields(String[] fields) {
        List<Param> items = new ArrayList<Param>(fields.length);
        for (String f : fields) items.add(makeField(f));
        return items;
    }

    private final Object content;
    private final Type type;
    private final Param[] modifiers;

    private Param(Object content, Type type, Param... modifiers) {
        if (content == null) throw new NullPointerException("ActionParameter content must be defined");
        this.content = content;
        this.type = type;
        this.modifiers = modifiers;
    }

    public Param addModifiers(Param... mods) {
        // Usual case of adding just one modifier
        if (modifiers == null) return new Param(content, type, mods);

        // Adding multiple
        Param[] combined = new Param[modifiers.length + mods.length];
        System.arraycopy(modifiers, 0, combined, 0, modifiers.length);
        System.arraycopy(mods, 0, combined, modifiers.length, mods.length);
        return new Param(content, type, combined);
    }

    public double asDouble() {
        if (type == Type.number) return ((Number) content).doubleValue();
        else return Double.parseDouble(content.toString());
    }

    /**
     * Return the name fo the matching field in the data
     *
     * @param data the data set to match into; if null no matching will be done and the name assumed correct
     * @return name as known in the data set
     */
    public String asField(Dataset data) {
        String name = content.toString();
        if (type == Type.field) {
            if (name.startsWith("#"))
                return name.toLowerCase();
            else if (data == null)
                return name;
            else {
                Field field = data.field(name, true);
                if (field == null) throw new IllegalArgumentException("Cannot find field '" + name + "' in the data");
                return field.name;
            }
        } else return "'" + name + "'";
    }

    /**
     * Return the name as a field name.
     * Since no data set is passed in, this does not do matching and assumes the paramter name is correct
     *
     * @return parameter name
     */
    public String asField() {
        return asField(null);
    }

    public String asString() {
        if (type == Type.number) return Data.formatNumeric(asDouble(), false);
        else return content.toString();
    }

    public int compareTo(Param o) {
        if (type != o.type) return type.ordinal() - o.type.ordinal();
        int n = Data.compare(content, o.content);
        if (n != 0) return n;
        for (int i = 0; i < modifiers.length && i < o.modifiers.length; i++) {
            int m = modifiers[i].compareTo(o.modifiers[i]);
            if (m != 0) return m;
        }
        return modifiers.length - o.modifiers.length;
    }

    public Param firstModifier() {
        return modifiers.length == 0 ? null : modifiers[0];
    }

    public boolean hasModifiers() {
        return modifiers.length > 0;
    }

    public int hashCode() {
        return 31 * content.hashCode() + type.hashCode();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Param that = (Param) o;
        return type == that.type && content.equals(that.content) && Arrays.equals(modifiers, that.modifiers);
    }

    public String toString() {
        // Strings are quoted
        StringBuilder b = new StringBuilder();
        if (type == Type.string) b.append('\'');
        b.append(asString());
        if (type == Type.string) b.append('\'');
        for (Param p : modifiers)
            b.append(':').append(p.toString());
        return b.toString();
    }

    public boolean isField() {
        return type == Type.field;
    }

    public Param[] modifiers() {
        return modifiers;
    }

    public Type type() {
        return type;
    }

    public enum Type {field, option, number, string}
}
