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

package org.brunel.data.io;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class serializes data items
 */
public class Serialize {

    public final static int DATA_SET = 1;
    public final static int FIELD = 2;
    public final static int NUMBER = 3;
    public final static int STRING = 4;

    /**
     * Return a serialized version of a dataset.
     *
     * @param data the dataset to serialize
     * @return an array of bytes representing this data
     */
    public static byte[] serializeDataset(Dataset data) {
        data = data.removeSpecialFields();
        ByteOutput s = new ByteOutput();

        // Basics, then each field
        s.addByte(DATA_SET).addNumber(data.fields.length);
        for (Field f : data.fields) addFieldToOutput(f, s);

        return s.asBytes();
    }

    /**
     * Return a serialized version of a field.
     *
     * @param field the field to serialize
     * @return an array of bytes representing this data
     */
    public static byte[] serializeField(Field field) {
        ByteOutput s = new ByteOutput();
        addFieldToOutput(field, s);
        return s.asBytes();
    }

    private static void addFieldToOutput(Field field, ByteOutput s) {
        // Basics
        int N = field.rowCount();
        s.addByte(FIELD).addString(field.name).addString(field.label);

        // Assemble map of data to indices
        Map<Object, Integer> items = new HashMap<Object, Integer>();
        List<Object> uniques = new ArrayList<Object>();
        for (int i = 0; i < N; i++) {
            Object value = field.value(i);
            if (!items.containsKey(value)) {
                items.put(value, items.size());
                uniques.add(value);
            }
        }

        // Add the unique data values
        s.addNumber(uniques.size());
        if (field.hasProperty("numeric")) {
            s.addByte(NUMBER);
            for (Object o : uniques) s.addNumber((Number) o);
        } else {
            s.addByte(STRING);
            for (Object o : uniques) s.addString((String) o);
        }

        // And now the values
        s.addNumber(N);
        for (int i = 0; i < N; i++) s.addNumber(items.get(field.value(i)));
    }

    public static Object deserialize(byte[] data) {
        ByteInput d = new ByteInput(data);
        return readFromByteInput(d);
    }

    private static Object readFromByteInput(ByteInput d) {
        byte b = d.readByte();
        if (b == FIELD) {
            // Fields have name, label, and the rows of data
            String name = d.readString();
            String label = d.readString();

            // Read number of unique values and their type
            int uniqueCount = d.readNumber().intValue();
            b = d.readByte();

            // Read unique values
            Object[] items = new Object[uniqueCount];
            for (int i = 0; i < uniqueCount; i++) {
                if (b == NUMBER)
                    items[i] = d.readNumber();
                else if (b == STRING)
                    items[i] = d.readNumber();
                else
                    throw new IllegalStateException("Unknown data column type " + b);
            }

            // Now create the actual data
            int len = d.readNumber().intValue();
            int[] indices = new int[len];
            for (int i=0; i<len; i++) indices[i] = d.readNumber().intValue();

            return Data.makeIndexedColumnField(name, label, items, indices);
        } else if (b == DATA_SET) {
            // Dataset consists of fields
            int len = d.readNumber().intValue();
            Field[] fields = new Field[len];
            for (int i = 0; i < len; i++) fields[i] = (Field) readFromByteInput(d);
            return new Dataset(fields);
        } else {
            throw new IllegalArgumentException("Unknown class: " + b);
        }
    }
}
