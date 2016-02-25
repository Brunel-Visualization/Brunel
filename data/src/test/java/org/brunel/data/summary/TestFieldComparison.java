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

package org.brunel.data.summary;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestFieldComparison {

    @Test
    public void testSort() {
        Field a = Fields.makeColumnField("A", null, new Object[]{1, 4, 2, 4, 6, 1});
        FieldRowComparison compare = new FieldRowComparison(new Field[] {a}, null, true);
        int[] order = compare.makeSortedOrder(a.rowCount());
        assertEquals("0, 5, 2, 1, 3, 4", Data.join(order));
    }
}
