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
import org.brunel.translator.JSTranslation;
import org.junit.Test;

import java.util.Date;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestField {

    @Test
    public void testEmptyField() {
        Field f = Fields.makeColumnField("a", "label", new Object[0]);
        assertEquals(0, f.rowCount());
    }

    @Test
    public void testProperties() {
        Field f = Fields.makeColumnField("a", "label", new Object[0]);
        assertEquals(null, f.property("xyz"));
        f.set("xyz", "12");
        assertEquals("12", f.strProperty("xyz"));
        assertEquals(12.00001, f.numProperty("xyz"), 0.001);

        f.set("abc", false);
        assertEquals(Boolean.FALSE, f.property("abc"));
        f.set("abc", true);
        assertEquals(Boolean.TRUE, f.property("abc"));

    }

    @Test
    public void testTiedValues() {
        Field f = Fields.makeColumnField("a", "label", new Object[]{200, 100, 400, 500, 100});
        Field g = Fields.makeColumnField("a", "label", new Object[]{200, 100, 400, 500, 600});
        Field h = Fields.makeColumnField("a", "label", new Object[]{200, 100, 400, 100, 200});
        Field i = Fields.makeColumnField("a", "label", new Object[]{100, 100, 100, 500, 500});
        Field j = Fields.makeColumnField("a", "label", new Object[]{1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 3, 4});

        assertEquals(100, f.numProperty("mode"), 0.01);
        assertEquals(400, g.numProperty("mode"), 0.01);
        assertEquals(100, h.numProperty("mode"), 0.01);
        assertEquals(100, i.numProperty("mode"), 0.01);
        assertEquals(3, j.numProperty("mode"), 0.01);
    }

    @Test
    public void testBasicFieldStats() {
        Field f = Fields.makeColumnField("a", "label", new Object[]{"1", "2", "a", "2", null, 0});
        Field g = Fields.makeColumnField("a", "label", new Object[]{100, 200, 400, 500, 600});
        Field h = Fields.makeColumnField("a", "label", new Object[]{"a", "b", "c", "c"});
        Field i = Fields.makeIndexingField("a", "label", 20);
        Field c = Fields.makeConstantField("a", "label", 5.0, 2000);

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
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
        assertEquals(2, counts[2]);

        assertEquals(4, f.uniqueValuesCount());
        assertEquals(5, g.uniqueValuesCount());
        assertEquals(3, h.uniqueValuesCount());
        assertEquals(20, i.uniqueValuesCount());
        assertEquals(1, c.uniqueValuesCount());
    }

    @Test
    public void testDate() {
        Field a;

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:00:01"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(1.0 / 3600 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.second, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:04:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(4.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:06:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(6.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 00:45:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(45.0 / 60 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.minute, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 04:59:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(DateUnit.hour, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 1, 1970 05:00:00"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(5.0 / 24, a.max(), 1e-9);
        assertEquals(DateUnit.hour, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 1, 1970", "January 4, 1970"}));
        assertEquals(0.0, a.min(), 1e-9);
        assertEquals(3.0, a.max(), 1e-9);
        assertEquals(DateUnit.day, a.property("dateUnit"));

        // Note that only three days are needed to get days as a unit -- this an exception to the usual "5 ticks" rule
        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 2, 1970"}));
        assertEquals(1.0, a.min(), 1e-9);
        assertEquals(9.0, a.max(), 1e-9);
        assertEquals(DateUnit.day, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "March 2, 1970"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(60.0, a.max(), 1e-9);
        assertEquals(DateUnit.week, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "December 31, 1970"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(364.0, a.max(), 1e-9);
        assertEquals(DateUnit.quarter, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1972"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(2 * 365 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.quarter, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1974"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(4 * 365 + 1 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.year, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 1976"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(6 * 365 + 1 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.year, a.property("dateUnit"));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 10, 1970", "January 10, 2030"}));
        assertEquals(9.0, a.min(), 1e-9);
        assertEquals(60 * 365 + 15 + 9, a.max(), 1e-9);
        assertEquals(DateUnit.decade, a.property("dateUnit"));

        a = Data.toDate(Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:24"}), null));
        assertEquals((9 + 45 / 60.0 + 22 / 3600.0) / 24.0, a.min(), 1e-9);
        assertEquals((9 + 45 / 60.0 + 24 / 3600.0) / 24.0, a.max(), 1e-9);
        assertEquals(DateUnit.second, a.property("dateUnit"));
    }

    @Test
    public void testMomentFieldStats() {
        Field uniform = Fields.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Fields.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Fields.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Fields.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});

        assertEquals(350, uniform.numProperty("mean"), 0.01);
        assertEquals(350, uniformWithMissing.numProperty("mean"), 0.01);
        assertEquals(2, peak.numProperty("mean"), 0.01);
        assertEquals(2.6, skew.numProperty("mean"), 0.01);

        assertEquals(187.08, uniform.numProperty("stddev"), 0.01);
        assertEquals(187.08, uniformWithMissing.numProperty("stddev"), 0.01);
        assertEquals(0.534, peak.numProperty("stddev"), 0.01);
        assertEquals(2.875, skew.numProperty("stddev"), 0.01);

        assertEquals(0, uniform.numProperty("skew"), 0.01);
        assertEquals(0, uniformWithMissing.numProperty("skew"), 0.01);
        assertEquals(0, peak.numProperty("skew"), 0.01);
        assertEquals(1.86, skew.numProperty("skew"), 0.01);

        assertEquals(-1.557, uniform.numProperty("kurtosis"), 0.01);
        assertEquals(-1.557, uniformWithMissing.numProperty("kurtosis"), 0.01);
        assertEquals(0.5, peak.numProperty("kurtosis"), 0.01);
        assertEquals(1.983, skew.numProperty("kurtosis"), 0.01);

    }

    @Test
    @JSTranslation(ignore = true)
    public void testJavaDates() {
        long feb_1_1970 = 60907593600000L;
        Field f = Fields.makeColumnField("test", "dates", new Object[]{
                new Date(feb_1_1970),
                new Date(feb_1_1970 + 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 2 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 3 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 4 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 5 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 6 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 7 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 8 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 9 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 10 * 1000 * 60 * 60 * 24),
                new Date(feb_1_1970 + 20 * 1000 * 60 * 60 * 24)
        });

        f.set("numeric", true);
        f.set("date", true);
        assertTrue(f.isDate());
        assertEquals("Feb 1 00:00", f.valueFormatted(0));
        assertEquals("Feb 2 00:00", f.valueFormatted(1));
        assertEquals("Feb 3 00:00", f.valueFormatted(2));
        assertEquals("Feb 11 00:00", f.valueFormatted(10));
        assertEquals("Feb 21 00:00", f.valueFormatted(11));

    }

    @Test
    public void testOrderFieldStats() {
        Field uniform = Fields.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field uniformWithMissing = Fields.makeColumnField("a", "label", new Object[]{100, 200, null, 300, "a", 400, 500, 600});
        Field peak = Fields.makeColumnField("b", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field skew = Fields.makeColumnField("c", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});
        Field a = Fields.makeColumnField("f", "label", new Object[]{0, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field b = Fields.makeColumnField("f", "label", new Object[]{10, 20, 30, 40, 22, 50, 60});

        assertEquals(350, uniform.numProperty("median"), 0.01);
        assertEquals(350, uniformWithMissing.numProperty("median"), 0.01);
        assertEquals(2, peak.numProperty("median"), 0.01);
        assertEquals(1.5, skew.numProperty("median"), 0.01);
        assertEquals(2, a.numProperty("median"), 0.01);
        assertEquals(30, b.numProperty("median"), 0.01);

        assertEquals(200, uniform.numProperty("q1"), 0.01);
        assertEquals(200, uniformWithMissing.numProperty("q1"), 0.01);
        assertEquals(2, peak.numProperty("q1"), 0.01);
        assertEquals(1, skew.numProperty("q1"), 0.01);
        assertEquals(1, a.numProperty("q1"), 0.01);
        assertEquals(21, b.numProperty("q1"), 0.01);

        assertEquals(500, uniform.numProperty("q3"), 0.01);
        assertEquals(500, uniformWithMissing.numProperty("q3"), 0.01);
        assertEquals(2, peak.numProperty("q3"), 0.01);
        assertEquals(2, skew.numProperty("q3"), 0.01);
        assertEquals(7.5, a.numProperty("q3"), 0.01);
        assertEquals(45, b.numProperty("q3"), 0.01);

        assertEquals(100, uniform.numProperty("granularity"), 0.01);
        assertEquals(100, uniformWithMissing.numProperty("granularity"), 0.01);
        assertEquals(1, peak.numProperty("granularity"), 0.01);
        assertEquals(1, skew.numProperty("granularity"), 0.01);
        assertEquals(1, a.numProperty("granularity"), 0.01);
        assertEquals(2, b.numProperty("granularity"), 0.01);
    }

    @Test
    public void testFieldNumericFormatting() {
        Field f = Fields.makeColumnField("a", "label", new Object[]{15.0, 16.0, 34.0, 56.0, 444.0});
        f = Data.toNumeric(f);
        assertEquals((Double) 0.0, f.numProperty("decimalPlaces"));
        assertEquals("15", f.valueFormatted(0));
        assertEquals("16", f.valueFormatted(1));
        assertEquals("444", f.valueFormatted(4));

        f = Fields.makeColumnField("a", "label", new Object[]{1.123456789, 2.3456789, 3.456789, 7.890123});
        f = Data.toNumeric(f);
        assertEquals((Double) 3.0, f.numProperty("decimalPlaces"));
        assertEquals("1.123", f.valueFormatted(0));
        assertEquals("2.346", f.valueFormatted(1));
        assertEquals("3.457", f.valueFormatted(2));

        f = Fields.makeColumnField("a", "label", new Object[]{123456, 234567, 345678});
        f = Data.toNumeric(f);
        assertEquals((Double) 0.0, f.numProperty("decimalPlaces"));
        assertEquals("123,456", f.valueFormatted(0));
        assertEquals("234,567", f.valueFormatted(1));
        assertEquals("345,678", f.valueFormatted(2));
        assertEquals("8", f.format(7.9));

        f = Fields.makeColumnField("a", "label", new Object[]{0.0});
        f = Data.toNumeric(f);
        assertEquals((Double) 4.0, f.numProperty("decimalPlaces"));

    }

}
