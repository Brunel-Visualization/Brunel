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

package org.brunel.data;

import org.brunel.data.util.Range;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestRange {

    @Test
    public void testRepresentations() {
        Range a = Range.make(0, 4);
        assertEquals("0\u20264", a.toString());
        assertEquals(2, Data.asNumeric(a), 0.01);

        Range b = Range.make(-200.5, -100);
        assertEquals("-200.5\u2026-100", b.toString());
        assertEquals(-150.25, Data.asNumeric(b), 0.01);

        Range c = Range.make(100, Double.POSITIVE_INFINITY);
        assertEquals("\u2265 100", c.toString());
        assertEquals(100, Data.asNumeric(c), 0.01);

        Range d = Range.make(Double.NEGATIVE_INFINITY, 100);
        assertEquals("< 100", d.toString());
        assertEquals(100, Data.asNumeric(d), 0.01);

        Range e = Range.make(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertEquals("\u2210", e.toString());
        assertEquals(null, Data.asNumeric(e));

        assertNotEquals(a.hashCode(), b.hashCode());

    }

}
