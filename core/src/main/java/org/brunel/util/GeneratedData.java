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

package org.brunel.util;

import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;

/**
 * Simple class so we can generate data sets simply
 */
public class GeneratedData {
    // The format for this is N , a , b , c ...
    // Where N is the length and a,b,c et.c are methods to generate data
    public static Dataset make(String description) {
        String[] parts = description.split("\\+");
        int N = Integer.parseInt(parts[0].trim());
        int M = parts.length - 1;
        Field[] fields = new Field[M];
        for (int i = 0; i < M; i++) {
            String what = parts[i + 1].trim();
            Object[] data = new Object[N];
            for (int r = 0; r < N; r++) {
                if (what.equals("row0")) data[r] = r;
                else if (what.equals("row")) data[r] = r + 1;
                else if (what.equals("random")) data[r] = Math.random();
                else data[i] = what;
            }
            fields[i] = Fields.makeColumnField("" + (char) ('a' + i), "" + (char) ('A' + i), data);
        }
        return Dataset.make(fields);
    }
}
