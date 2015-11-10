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

public class TestDatalessField {

    @Test
    public void testFieldStats() {
        Field uniform = Data.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Data.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Data.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Data.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});
        Field a = Data.makeColumnField("f", "label", new Object[]{0, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field b = Data.makeColumnField("f", "label", new Object[]{10, 20, 30, 40, 22, 50, 60});

        uniform = uniform.dropData();
        uniformWithMissing = uniformWithMissing.dropData();
        peak = peak.dropData();
        skew = skew.dropData();
        a = a.dropData();
        b = b.dropData();

        // Basics
        assertEquals(6, uniform.rowCount());
        assertEquals(8, uniformWithMissing.rowCount());

        assertEquals(350, uniform.numericProperty("median"), 0.01);
        assertEquals(350, uniformWithMissing.numericProperty("median"), 0.01);
        assertEquals(2, peak.numericProperty("median"), 0.01);
        assertEquals(1.5, skew.numericProperty("median"), 0.01);
        assertEquals(2, a.numericProperty("median"), 0.01);
        assertEquals(30, b.numericProperty("median"), 0.01);

        assertEquals(200, uniform.numericProperty("q1"), 0.01);
        assertEquals(200, uniformWithMissing.numericProperty("q1"), 0.01);
        assertEquals(2, peak.numericProperty("q1"), 0.01);
        assertEquals(1, skew.numericProperty("q1"), 0.01);
        assertEquals(1, a.numericProperty("q1"), 0.01);
        assertEquals(21, b.numericProperty("q1"), 0.01);

        assertEquals(500, uniform.numericProperty("q3"), 0.01);
        assertEquals(500, uniformWithMissing.numericProperty("q3"), 0.01);
        assertEquals(2, peak.numericProperty("q3"), 0.01);
        assertEquals(2, skew.numericProperty("q3"), 0.01);
        assertEquals(7.5, a.numericProperty("q3"), 0.01);
        assertEquals(45, b.numericProperty("q3"), 0.01);

        assertEquals(100, uniform.numericProperty("granularity"), 0.01);
        assertEquals(100, uniformWithMissing.numericProperty("granularity"), 0.01);
        assertEquals(1, peak.numericProperty("granularity"), 0.01);
        assertEquals(1, skew.numericProperty("granularity"), 0.01);
        assertEquals(1, a.numericProperty("granularity"), 0.01);
        assertEquals(2, b.numericProperty("granularity"), 0.01);
    }

}
