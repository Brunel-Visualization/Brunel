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

package org.brunel.data.values;

import org.brunel.data.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ColumnProvider implements Provider {

    public static Provider copy(Provider base) {
        Object[] data = new Object[base.count()];
        for (int i = 0; i < data.length; i++) data[i] = base.value(i);
        return new ColumnProvider(data);
    }

    private final Object[] column;

    public ColumnProvider(Object[] column) {
        // Use a common store so common copies are not duplicated
        Map<Object, Object> common = new HashMap<Object, Object>();
        this.column = new Object[column.length];
        for (int i = 0; i < column.length; i++) {
            Object value = column[i];
            Object stored = common.get(value);
            if (stored == null) {
                common.put(value, value);
                this.column[i] = value;
            } else {
                this.column[i] = stored;
            }
        }
    }

    public int count() {
        return column.length;
    }

    public int expectedSize() {
        Set<Object> seen = new HashSet<Object>();
        int total = 24 + 4 * column.length;
        for (Object c : column) {
            if (c == null) continue;
            if (seen.add(c)) {
                if (c instanceof String)
                    total += (42 + ((String) c).length() * 2);
                else
                    total += 16;
            }
        }
        return total;
    }

    public Provider setValue(Object o, int index) {
        column[index] = o;
        return this;
    }

    public int compareRows(int a, int b, HashMap<Object, Integer> categoryOrder) {
        // Use the defined order if given
        Object p = column[a];
        Object q = column[b];
        if (p == q) return 0;
        if (p == null) return 1;
        if (q == null) return -1;
        if (categoryOrder.isEmpty())
            return Data.compare(p, q);
        else
            return categoryOrder.get(p) - categoryOrder.get(q);
    }

    public Object value(int index) {
        return column[index];
    }

}
