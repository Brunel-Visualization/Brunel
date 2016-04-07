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

package org.brunel.data.diagram;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.util.MapInt;

/**
 * A chord diagram shows sized links between two categorical fields.
 * This class takes the data in standard format and converts it into a form that D3 can use
 */
public class Chord {

    public static Chord make(Dataset data, String fieldA, String fieldB, String fieldSize) {
        return new Chord(data, fieldA, fieldB, fieldSize);
    }

    private final double[][] mtx;
    private final int[][] idx;
    private final Object[] names;

    public Chord(Dataset data, String fieldA, String fieldB, String fieldSize) {
        Field a = data.field(fieldA);
        Field b = data.field(fieldB);
        Field s = data.field(fieldSize);

        // Map to indices
        MapInt indices = new MapInt()
                .index(a.categories())
                .index(b.categories());

        int N = indices.size();

        // Build names list
        names = indices.getIndexedKeys();

        // Build matrix and index reference
        mtx = new double[N][N];
        idx = new int[N][N];

        // Fill matrices and indices with values
        for (int i = 0; i < a.rowCount(); i++) {
            Double size = s == null ? 1 : Data.asNumeric(s.value(i));
            if (size > 0) {
                Integer i1 = indices.get(a.value(i));
                Integer i2 = indices.get(b.value(i));
                mtx[i1][i2] += size;
                mtx[i2][i1] += size;
                idx[i1][i2] = i;
                idx[i2][i1] = i;
            }
        }
    }

    public Object group(int i) {
        return names[i];
    }

    public int index(int from, int to) {
        return idx[from][to];
    }

    public double[][] matrix() {
        return mtx;
    }

}


