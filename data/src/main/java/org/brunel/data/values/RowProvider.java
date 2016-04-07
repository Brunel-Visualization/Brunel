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

package org.brunel.data.values;

import org.brunel.data.util.MapInt;

public class RowProvider implements Provider {

    private final int len;

    public RowProvider(int len) {
        this.len = len;
    }

    public int count() {
        return len;
    }

    public int expectedSize() {
        return 24;
    }

    public Provider setValue(Object o, int index) {
        return ColumnProvider.copy(this).setValue(o, index);
    }

    public Object value(int index) {
        return index + 1;
    }

    public int compareRows(int a, int b, MapInt categoryOrder) {
        return a-b;
    }
}
