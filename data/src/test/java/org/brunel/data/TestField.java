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

import org.brunel.data.util.DateUnit;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestField {

    @Test
    public void testEmptyField() {
        Field f = Data.makeColumnField("a", "label", new Object[0]);
        assertEquals(0, f.rowCount());
    }

    @Test
    public void testProperties() {
        Field f = Data.makeColumnField("a", "label", new Object[0]);
        assertEquals(null, f.property("xyz"));
        f.set("xyz", "12");
        assertEquals("12", f.stringProperty("xyz"));
        assertEquals(12.00001, f.numericProperty("xyz"), 0.001);

        f.set("abc", false);
        assertEquals(Boolean.FALSE, f.property("abc"));
        f.set("abc", true);
        assertEquals(Boolean.TRUE, f.property("abc"));

    }

    @Test
    public void testTiedValues() {
        Field f = Data.makeColumnField("a", "label", new Object[]{200, 100, 400, 500, 100});
        Field g = Data.makeColumnField("a", "label", new Object[]{200, 100, 400, 500, 600});
        Field h = Data.makeColumnField("a", "label", new Object[]{200, 100, 400, 100, 200});
        Field i = Data.makeColumnField("a", "label", new Object[]{100, 100, 100, 500, 500});
        Field j = Data.makeColumnField("a", "label", new Object[]{1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 3, 4});

        assertEquals(100, f.numericProperty("mode"), 0.01);
        assertEquals(400, g.numericProperty("mode"), 0.01);
        assertEquals(100, h.numericProperty("mode"), 0.01);
        assertEquals(100, i.numericProperty("mode"), 0.01);
        assertEquals(3, j.numericProperty("mode"), 0.01);
    }

    @Test
    public void testBasicFieldStats() {
        Field f = Data.makeColumnField("a", "label", new Object[]{"1", "2", "a", "2", null, 0});
        Field g = Data.makeColumnField("a", "label", new Object[]{100, 200, 400, 500, 600});
        Field h = Data.makeColumnField("a", "label", new Object[]{"a", "b", "c", "c"});
        Field i = Data.makeIndexingField("a", "label", 20);
        Field c = Data.makeConstantField("a", "label", 5.0, 2000);

        assertEquals(0, f.min(), 0.01);
        assertEquals(2, f.max(), 0.01);

        assertEquals(100, g.min(), 0.01);
        assertEquals(600, g.max(), 0.01);

        assertEquals(null, h.min());
        assertEquals(null, h.max());

        assertEquals(1, i.min(), 0.01);
        assertEquals(20, i.max(), 0.01);

        assertEquals(5.0, c.min(), 0.01);
        assertEquals(5.0, c.max(), 0.01);

        int[] counts = (int[]) h.property("categoryCounts");
        Assert.assertEquals(1, counts[0]);
        Assert.assertEquals(1, counts[1]);
        Assert.assertEquals(2, counts[2]);

        assertEquals(4, f.uniqueValuesCount());
        assertEquals(5, g.uniqueValuesCount());
        assertEquals(3, h.uniqueValuesCount());
        assertEquals(20, i.uniqueValuesCount());
        assertEquals(1, c.uniqueValuesCount());
    }

    @Test
    public void testDate() {
        Field a;

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:00:01"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(1.0 / 3600 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.second, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:04:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(4.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:06:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(6.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:45:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(45.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 04:59:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(DateUnit.hour, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 05:00:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(5.0 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.hour, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 4, 1970"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(3.0, a.max(), 1e-9);
        assertEquals(DateUnit.day, a.property("dateUnit"));

        // Note that only three days are needed to get days as a unit -- this an exception to the usual "5 ticks" rule
        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 2, 1970"}));
        assertEquals(1.0, a.min(), 1e-9);
        assertEquals(9.0, a.max(), 1e-9);
        assertEquals(DateUnit.day, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "March 2, 1970"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(60.0, a.max(), 1e-9);
        assertEquals(DateUnit.week, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "December 31, 1970"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(364.0, a.max(), 1e-9);
        assertEquals(DateUnit.quarter, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1972"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(2 * 365 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.quarter, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1974"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(4 * 365 + 1 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.year, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1976"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(6 * 365 + 1 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.year, a.property("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 2030"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(60 * 365 + 15 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.decade, a.property("dateUnit"));

        a = Data.toDate(Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:24"}), null));
        assertEquals((9 + 45 / 60.0 + 22 / 3600.0) / 24.0, a.min(), 1e-9);
        assertEquals((9 + 45 / 60.0 + 24 / 3600.0) / 24.0, a.max(), 1e-9);
        assertEquals(DateUnit.second, a.property("dateUnit"));
    }

    @Test
    public void testMomentFieldStats() {
        Field uniform = Data.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Data.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Data.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Data.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});

        assertEquals(350, uniform.numericProperty("mean"), 0.01);
        assertEquals(350, uniformWithMissing.numericProperty("mean"), 0.01);
        assertEquals(2, peak.numericProperty("mean"), 0.01);
        assertEquals(2.6, skew.numericProperty("mean"), 0.01);

        assertEquals(187.08, uniform.numericProperty("stddev"), 0.01);
        assertEquals(187.08, uniformWithMissing.numericProperty("stddev"), 0.01);
        assertEquals(0.534, peak.numericProperty("stddev"), 0.01);
        assertEquals(2.875, skew.numericProperty("stddev"), 0.01);

        assertEquals(0, uniform.numericProperty("skew"), 0.01);
        assertEquals(0, uniformWithMissing.numericProperty("skew"), 0.01);
        assertEquals(0, peak.numericProperty("skew"), 0.01);
        assertEquals(1.86, skew.numericProperty("skew"), 0.01);

        assertEquals(-1.557, uniform.numericProperty("kurtosis"), 0.01);
        assertEquals(-1.557, uniformWithMissing.numericProperty("kurtosis"), 0.01);
        assertEquals(0.5, peak.numericProperty("kurtosis"), 0.01);
        assertEquals(1.983, skew.numericProperty("kurtosis"), 0.01);

    }

    @Test
    public void testOrderFieldStats() {
        Field uniform = Data.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Data.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Data.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Data.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});
        Field a = Data.makeColumnField("f", "label", new Object[]{0, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field b = Data.makeColumnField("f", "label", new Object[]{10, 20, 30, 40, 22, 50, 60});

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
