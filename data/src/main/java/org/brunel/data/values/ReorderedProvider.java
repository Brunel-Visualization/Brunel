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

import java.util.HashMap;

public class ReorderedProvider implements Provider {

    private final Provider base;
    private final int[] order;

    public ReorderedProvider(Provider base, int[] order) {
        if (base instanceof ReorderedProvider) {
            // Convolute the two orders and go directly to the original
            ReorderedProvider other = (ReorderedProvider) base;
            this.base = other.base;
            this.order = new int[order.length];
            for (int i=0; i<order.length; i++) this.order[i] = other.order[order[i]];
        } else {
            this.base = base;
            this.order = order;
        }
    }

    public int compareRows(int a, int b, HashMap<Object, Integer> categoryOrder) {
        return base.compareRows(order[a], order[b], categoryOrder);
    }

    public int count() {
        return order.length;
    }

    public int expectedSize() {
        return 24 + order.length * 4 + base.expectedSize();
    }

    public Provider setValue(Object o, int index) {
        return ColumnProvider.copy(this).setValue(o, index);
    }

    public Object value(int index) {
        return base.value(order[index]);
    }

}
