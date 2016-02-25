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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestProviders {

    @Test
    public void testConstant() {
        Field a = Fields.makeConstantField("a", "b", "foo", 50);
        assertEquals("a", a.name);
        assertEquals("b", a.label);
        assertEquals(50, a.rowCount());
        assertEquals(50, a.numericProperty("valid"), 0.001);
        assertEquals(0, a.numericProperty("validNumeric"), 0.001);
        assertEquals(1, a.numericProperty("unique"), 0.001);
        assertEquals("foo", a.value(2));
        assertEquals(false, a.isNumeric());
    }

    @Test
    public void testIndexing() {
        Field a = Fields.makeIndexingField("a", "b", 30);
        assertEquals("a", a.name);
        assertEquals("b", a.label);
        assertEquals(30, a.rowCount());
        assertEquals(30, a.numericProperty("valid"), 0.001);
        assertEquals(30, a.numericProperty("validNumeric"), 0.001);
        assertEquals(30, a.numericProperty("unique"), 0.001);
        assertEquals(15.5, a.numericProperty("mean"), 0.001);
        assertEquals(3, a.value(2));
        assertEquals(8, a.value(7));
        assertEquals(true, a.isNumeric());
    }

    @Test
    public void testPermute() {
        Field base = Fields.makeIndexingField("a", "b", 10);
        Field a = Fields.permute(base, new int[]{0, 0, 0, 1, 1, 2, 2, 3}, false);
        assertEquals(true, a.isNumeric());
        assertEquals("a", a.name);
        assertEquals("b", a.label);
        assertEquals(8, a.rowCount());
        assertEquals(8, a.numericProperty("valid"), 0.001);
        assertEquals(8, a.numericProperty("validNumeric"), 0.001);
        assertEquals(4, a.numericProperty("unique"), 0.001);
        assertEquals((1 + 1 + 1 + 2 + 2 + 3 + 3 + 4) / 8.0, a.numericProperty("mean"), 0.001);
        assertEquals(1, a.value(1));
        assertEquals(1, a.value(2));
        assertEquals(4, a.value(7));
        assertEquals("1, 2, 3, 4", Data.join(a.categories()));
    }

}
