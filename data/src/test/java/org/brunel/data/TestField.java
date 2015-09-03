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
        assertEquals(false, f.hasProperty("xyz"));
        f.setProperty("xyz", "12");
        assertEquals(true, f.hasProperty("xyz"));
        assertEquals("12", f.getStringProperty("xyz"));
        assertEquals(12.00001, f.getNumericProperty("xyz"), 0.001);

        assertEquals(false, f.hasProperty("abc"));
        f.setProperty("abc", false);
        assertEquals(Boolean.FALSE, f.getProperty("abc"));
        assertEquals(false, f.hasProperty("abc"));
        f.setProperty("abc", true);
        assertEquals(Boolean.TRUE, f.getProperty("abc"));
        assertEquals(true, f.hasProperty("abc"));

    }

    @Test
    public void testTiedValues() {
        Field f = Data.makeColumnField("a", "label", new Object[]{200, 100, 400, 500, 100});
        Field g = Data.makeColumnField("a", "label", new Object[]{200, 100, 400, 500, 600});
        Field h = Data.makeColumnField("a", "label", new Object[]{200, 100, 400, 100, 200});
        Field i = Data.makeColumnField("a", "label", new Object[]{100, 100, 100, 500, 500});
        Field j = Data.makeColumnField("a", "label", new Object[]{1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 3, 4});

        assertEquals(100, f.getNumericProperty("mode"), 0.01);
        assertEquals(400, g.getNumericProperty("mode"), 0.01);
        assertEquals(100, h.getNumericProperty("mode"), 0.01);
        assertEquals(100, i.getNumericProperty("mode"), 0.01);
        assertEquals(3, j.getNumericProperty("mode"), 0.01);
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

        int[] counts = (int[]) h.getProperty("categoryCounts");
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
        assertEquals(DateUnit.second, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:04:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(4.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:06:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(6.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:45:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(45.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 04:59:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(DateUnit.hour, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 05:00:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(5.0 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.hour, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 4, 1970"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(3.0, a.max(), 1e-9);
        assertEquals(DateUnit.day, a.getProperty("dateUnit"));

        // Note that only three days are needed to get days as a unit -- this an exception to the usual "5 ticks" rule
        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 2, 1970"}));
        assertEquals(1.0, a.min(), 1e-9);
        assertEquals(9.0, a.max(), 1e-9);
        assertEquals(DateUnit.day, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "March 2, 1970"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(60.0, a.max(), 1e-9);
        assertEquals(DateUnit.week, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "December 31, 1970"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(364.0, a.max(), 1e-9);
        assertEquals(DateUnit.quarter, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1972"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(2 * 365 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.quarter, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1974"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(4 * 365 + 1 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.year, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1976"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(6 * 365 + 1 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.year, a.getProperty("dateUnit"));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 2030"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(60 * 365 + 15 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.decade, a.getProperty("dateUnit"));

        a = Data.toDate(Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:24"}), null));
        assertEquals((9 + 45 / 60.0 + 22 / 3600.0) / 24.0, a.min(), 1e-9);
        assertEquals((9 + 45 / 60.0 + 24 / 3600.0) / 24.0, a.max(), 1e-9);
        assertEquals(DateUnit.second, a.getProperty("dateUnit"));
    }

    @Test
    public void testMomentFieldStats() {
        Field uniform = Data.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Data.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Data.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Data.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});

        assertEquals(350, uniform.getNumericProperty("mean"), 0.01);
        assertEquals(350, uniformWithMissing.getNumericProperty("mean"), 0.01);
        assertEquals(2, peak.getNumericProperty("mean"), 0.01);
        assertEquals(2.6, skew.getNumericProperty("mean"), 0.01);

        assertEquals(187.08, uniform.getNumericProperty("stddev"), 0.01);
        assertEquals(187.08, uniformWithMissing.getNumericProperty("stddev"), 0.01);
        assertEquals(0.534, peak.getNumericProperty("stddev"), 0.01);
        assertEquals(2.875, skew.getNumericProperty("stddev"), 0.01);

        assertEquals(0, uniform.getNumericProperty("skew"), 0.01);
        assertEquals(0, uniformWithMissing.getNumericProperty("skew"), 0.01);
        assertEquals(0, peak.getNumericProperty("skew"), 0.01);
        assertEquals(1.86, skew.getNumericProperty("skew"), 0.01);

        assertEquals(-1.557, uniform.getNumericProperty("kurtosis"), 0.01);
        assertEquals(-1.557, uniformWithMissing.getNumericProperty("kurtosis"), 0.01);
        assertEquals(0.5, peak.getNumericProperty("kurtosis"), 0.01);
        assertEquals(1.983, skew.getNumericProperty("kurtosis"), 0.01);

    }

    @Test
    public void testOrderFieldStats() {
        Field uniform = Data.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Data.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Data.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Data.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});
        Field a = Data.makeColumnField("f", "label", new Object[]{0, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field b = Data.makeColumnField("f", "label", new Object[]{10, 20, 30, 40, 22, 50, 60});

        assertEquals(350, uniform.getNumericProperty("median"), 0.01);
        assertEquals(350, uniformWithMissing.getNumericProperty("median"), 0.01);
        assertEquals(2, peak.getNumericProperty("median"), 0.01);
        assertEquals(1.5, skew.getNumericProperty("median"), 0.01);
        assertEquals(2, a.getNumericProperty("median"), 0.01);
        assertEquals(30, b.getNumericProperty("median"), 0.01);

        assertEquals(200, uniform.getNumericProperty("q1"), 0.01);
        assertEquals(200, uniformWithMissing.getNumericProperty("q1"), 0.01);
        assertEquals(2, peak.getNumericProperty("q1"), 0.01);
        assertEquals(1, skew.getNumericProperty("q1"), 0.01);
        assertEquals(1, a.getNumericProperty("q1"), 0.01);
        assertEquals(21, b.getNumericProperty("q1"), 0.01);

        assertEquals(500, uniform.getNumericProperty("q3"), 0.01);
        assertEquals(500, uniformWithMissing.getNumericProperty("q3"), 0.01);
        assertEquals(2, peak.getNumericProperty("q3"), 0.01);
        assertEquals(2, skew.getNumericProperty("q3"), 0.01);
        assertEquals(7.5, a.getNumericProperty("q3"), 0.01);
        assertEquals(45, b.getNumericProperty("q3"), 0.01);

        assertEquals(100, uniform.getNumericProperty("granularity"), 0.01);
        assertEquals(100, uniformWithMissing.getNumericProperty("granularity"), 0.01);
        assertEquals(1, peak.getNumericProperty("granularity"), 0.01);
        assertEquals(1, skew.getNumericProperty("granularity"), 0.01);
        assertEquals(1, a.getNumericProperty("granularity"), 0.01);
        assertEquals(2, b.getNumericProperty("granularity"), 0.01);
    }

}
