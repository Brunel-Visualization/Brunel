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

import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestRange {

    @Test
    public void testRepresentations() {
        Range a = Range.make((double) 0, (double) 4, null);
        assertEquals("0\u20264", a.toString());
        assertEquals(2, Data.asNumeric(a), 0.01);

        double low = -200.5;
        double high = -100;
        Range b = Range.make(low, high, null);
        assertEquals("-200.5\u2026-100", b.toString());
        assertEquals(-150.25, Data.asNumeric(b), 0.01);

        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEquality() {
        Date d1a = new Date(233444);
        Date d1b = new Date(233444);
        Date d2 = new Date(233455544);

        assertEquals(d1a, d1b);
        Range r1 = Range.makeDateNative(d1a, d2, true, DateFormat.HourMinSec);
        Range r2 = Range.makeDateNative(d1b, d2, true, DateFormat.HourMinSec);

        assertEquals(r1, r2);



    }

}
