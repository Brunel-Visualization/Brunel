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

package org.brunel.data.modify;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.data.auto.NumericScale;
import org.brunel.data.util.ItemsList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class TestAuto {

    @Test
    public void testAutoConvertDate() {
        // Make some dates
        Date d1 = Data.asDate("1971-1-3");
        Date d2 = Data.asDate("1971-1-9");
        Date d3 = Data.asDate("1971-1-31");

        Field a = Data.makeColumnField("field", null, new Object[]{d1, d2, d3, null});
        Field b = Auto.convert(a);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(true, b.isDate());

        // Should ignore one error
        a = Data.makeColumnField("field", null, new Object[]{d1, d2, "oops", d3, null, null});
        b = Auto.convert(a);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(true, b.isDate());
        assertEquals(3, b.valid());

        a = Data.makeColumnField("field", null, new Object[]{"1971-1-3", "1971-1-12"});
        b = Auto.convert(a);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(true, b.isDate());
        assertEquals(2, b.valid());
        assertEquals(9.0, b.max() - b.min(), 0.001);

        a = Data.makeColumnField("field", null, new Object[]{1970, 1972, 1978});
        b = Auto.convert(a);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(true, b.isDate());
        assertEquals(3, b.valid());
        assertEquals(8 * 365 + 2, b.max() - b.min(), 0.001);

        a = Data.makeColumnField("field", null, new Object[]{1970, 1971.5, 1978});
        b = Auto.convert(a);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(false, b.isDate());
        assertEquals(3, b.valid());
        assertEquals(8, b.max() - b.min(), 0.001);

    }

    @Test
    public void testAutoConvertNumeric() {
        Field a = Data.makeColumnField("field", null, new Object[]{1, 2, 3, 4});
        Field b = Auto.convert(a);
        assertEquals(a, b);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(false, b.isDate());
        Assert.assertEquals(2.5, b.numericProperty("mean"), 0.001);

        a = Data.makeColumnField("field", null, new Object[]{"1", "2", "3", "4"});
        b = Auto.convert(a);
        Assert.assertEquals(false, a == b);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(false, b.isDate());
        Assert.assertEquals(2.5, b.numericProperty("mean"), 0.001);

        a = Data.makeColumnField("field", null, new Object[]{"a", "2", "3", "4"});
        b = Auto.convert(a);
        Assert.assertEquals(false, a == b);
        Assert.assertEquals(true, b.isNumeric());
        Assert.assertEquals(false, b.isDate());
        Assert.assertEquals(3.0, b.numericProperty("mean"), 0.001);

        a = Data.makeColumnField("field", null, new Object[]{"a", "2", "c", "4"});
        b = Auto.convert(a);
        Assert.assertEquals(true, a == b);
        Assert.assertEquals(false, b.isNumeric());
        Assert.assertEquals(false, b.isDate());
    }

    @Test
    public void testAutoConvertLists() {
        // TODO: Make work in JS
        Field a = Data.makeColumnField("field", null, new Object[]{"a,b,c", "c,a", null, "a ,e", ""});
        Field b = Auto.convert(a);
        Assert.assertEquals(false, a == b);
        Assert.assertEquals(false, b.isNumeric());
        Assert.assertEquals(false, b.isDate());
        Assert.assertEquals(new ItemsList(new String[]{"a", "b", "c"}, null), b.value(0));
        Assert.assertEquals(new ItemsList(new String[]{"c", "a"}, null), b.value(1));
        Assert.assertEquals(null, b.value(2));
        Assert.assertEquals(new ItemsList(new String[]{"a", "e"}, null), b.value(3));
        Assert.assertEquals(new ItemsList(new String[]{}, null), b.value(4));

    }

    @Test
    public void testChooseTransform() {
        Field a = Data.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field b = Data.makeColumnField("b", "label", new Object[]{"a", "b", "c"});
        Field c = Data.makeColumnField("d", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field d = Data.makeColumnField("d", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});
        Field e = Data.makeColumnField("e", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field f = Data.makeColumnField("f", "label", new Object[]{0, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field g = Data.makeColumnField("g", "label", new Object[]{-1, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});

        Auto.setTransform(a);
        Auto.setTransform(b);
        Auto.setTransform(c);
        Auto.setTransform(d);
        Auto.setTransform(e);
        Auto.setTransform(f);
        Auto.setTransform(g);

        Assert.assertEquals("linear", a.stringProperty("transform"));
        Assert.assertEquals("linear", b.stringProperty("transform"));
        Assert.assertEquals("linear", c.stringProperty("transform"));
        Assert.assertEquals("root", d.stringProperty("transform"));
        Assert.assertEquals("log", e.stringProperty("transform"));
        Assert.assertEquals("root", f.stringProperty("transform"));
        Assert.assertEquals("linear", g.stringProperty("transform"));
    }

    @Test
    public void testDateScaleDaysPlusRange() {
        double[] pad = {0, 0};

        Field a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 9, 2010"}));
        Assert.assertEquals("date : 2000 2020 : |2005|2010|2015|2020",
                asString(NumericScale.makeDateScale(a, false, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 10, 2010"}));
        Assert.assertEquals("date : 00:00 00:00 : |00:00|06:00|12:00|18:00|00:00",
                asString(NumericScale.makeDateScale(a, false, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 14, 2010"}));
        Assert.assertEquals(
                "date : Jan 9, 2010 Jan 14, 2010 : |Jan 9, 2010|Jan 10, 2010|Jan 11, 2010|Jan 12, 2010|Jan 13, 2010|Jan 14, 2010",
                asString(NumericScale.makeDateScale(a, false, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 14, 2010"}));
        Assert.assertEquals(
                "date : Jan 9, 2010 Jan 14, 2010 : |Jan 9, 2010|Jan 10, 2010|Jan 11, 2010|Jan 12, 2010|Jan 13, 2010|Jan 14, 2010",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2010"}));
        Assert.assertEquals("date : Jan 2010 Aug 2010 : |Mar 2010|May 2010|Jul 2010",
                asString(NumericScale.makeDateScale(a, false, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2010"}));
        Assert.assertEquals("date : Jan 2010 Sep 2010 : |Jan 2010|Mar 2010|May 2010|Jul 2010|Sep 2010",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2011"}));
        Assert.assertEquals("date : Jan 2010 Jan 2012 : |Jan 2010|Jul 2010|Jan 2011|Jul 2011|Jan 2012",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2014"}));
        Assert.assertEquals("date : Jan 2010 Jan 2015 : |Jan 2010|Jan 2011|Jan 2012|Jan 2013|Jan 2014|Jan 2015",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2024"}));
        Assert.assertEquals("date : 2010 2025 : |2010|2015|2020|2025",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2124"}));
        Assert.assertEquals("date : 2000 2140 : |2000|2020|2040|2060|2080|2100|2120|2140",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 6124"}));
        Assert.assertEquals("date : 2000 6500 : |2000|2500|3000|3500|4000|4500|5000|5500|6000|6500",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

    }

    @Test
    public void testDateScaleHoursOrLess() {
        Field a;
        double[] pad = {0, 0};

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:24"}));
        Assert.assertEquals("date : 09:45:22 09:45:24 : |09:45:22|09:45:23|09:45:24",
                asString(NumericScale.makeDateScale(a, false, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:54"}));
        assertEquals(0.40650462963, a.min(), 1e-9);
        assertEquals(0.406875, a.max(), 1e-9);
        Assert.assertEquals("date : 09:45:22 09:45:54 : |09:45:30|09:45:40|09:45:50",
                asString(NumericScale.makeDateScale(a, false, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:54"}));
        Assert.assertEquals("date : 09:45:20 09:46:00 : |09:45:20|09:45:30|09:45:40|09:45:50|09:46:00",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "10:01:54"}));
        Assert.assertEquals("date : 09:45 10:05 : |09:45|09:50|09:55|10:00|10:05",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "12:01:54"}));
        Assert.assertEquals("date : 09:30 12:30 : |09:30|10:00|10:30|11:00|11:30|12:00|12:30",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

        a = Data.toDate(Data.makeColumnField("a", "label", new Object[]{"09:45:22", "23:01:54"}));
        Assert.assertEquals("date : 09:00 00:00 : |09:00|12:00|15:00|18:00|21:00|00:00",
                asString(NumericScale.makeDateScale(a, true, pad, 5), a));

    }

    @Test
    public void testPadding() {
        Field a = Data.toNumeric(Data.makeColumnField("a", "label", new Object[]{100, 200, 120, 200, 3100}));
        a.set("transform", "linear");
        Assert.assertEquals("linear : 70 3130 : |500|1000|1500|2000|2500|3000",
                asString(NumericScale.makeLinearScale(a, false, 0.0, new double[]{0.01, 0.01}, 5, false), a));

        // Does not pad past zero
        Assert.assertEquals("linear : -200 3400 : |0|1000|2000|3000",
                asString(NumericScale.makeLinearScale(a, false, 0.0, new double[]{0.1, 0.1}, 5, false), a));
    }

    @Test
    public void testLinearScale() {
        double[] pad = {0, 0};

        Field a = Data.makeColumnField("a", "label", new Object[]{2, 4, 7, 120, 45, 120, 200, 3345});
        a = Data.toNumeric(a);
        a.set("transform", "linear");
        Assert.assertEquals("linear : 2 3345 : |500|1000|1500|2000|2500|3000",
                asString(NumericScale.makeLinearScale(a, false, 0.0, pad, 5, false), a));
        Assert.assertEquals("linear : 2 3345 : |200|400|600|800|1000|1200|1400|1600|1800|2000|2200|2400|2600|2800|3000|3200",
                asString(NumericScale.makeLinearScale(a, false, 0.0, pad, 15, false), a));
        Assert.assertEquals("linear : 2 3345 : |1000|2000|3000", asString(NumericScale.makeLinearScale(a, false, 0.0, pad, 2, false), a));
        Assert.assertEquals("linear : 2 3345 : |2000", asString(NumericScale.makeLinearScale(a, false, 0.0, pad, 1, false), a));
        Assert.assertEquals("linear : 0 3345 : |0|500|1000|1500|2000|2500|3000",
                asString(NumericScale.makeLinearScale(a, false, 0.01, pad, 7, false), a));
        Assert.assertEquals("linear : 0 3500 : |0|500|1000|1500|2000|2500|3000|3500",
                asString(NumericScale.makeLinearScale(a, true, 0.0, pad, 7, false), a));

        a = Data.makeColumnField("a", "label", new Object[]{-22.2, -22.201, -22.9, -22.7});
        a = Data.toNumeric(a);
        Assert.assertEquals("linear : -23 -22 : |-23|-22.8|-22.6|-22.4|-22.2|-22",
                asString(Auto.makeNumericScale(a, true, pad, 0.0, 5, false), a));
        Assert.assertEquals("linear : -22.9 -22.2 : |-22.8|-22.6|-22.4|-22.2",
                asString(NumericScale.makeLinearScale(a, false, 0.0, pad, 4, false), a));
        Assert.assertEquals("linear : -23 -22 : |-23|-22.5|-22",
                asString(Auto.makeNumericScale(a, true, pad, 0.0, 4, false), a));
        Assert.assertEquals("linear : -23 -22 : |-23|-22.5|-22",
                asString(Auto.makeNumericScale(a, true, pad, 0.95, 4, false), a));
        Assert.assertEquals("linear : -30 0 : |-30|-20|-10|0", asString(NumericScale.makeLinearScale(a, true, 0.99, pad, 4, false), a));
    }

    @Test
    public void testLogScale() {
        Field a = Data.makeColumnField("a", "label", new Object[]{2, 4, 7, 120, 45, 120, 200, 3345});
        a = Data.toNumeric(a);
        double[] pad = {0, 0};
        Assert.assertEquals("log : 2 3345 : |10|100|1000", asString(NumericScale.makeLogScale(a, false, pad, 0.0, 3), a));
        Assert.assertEquals("log : 1 3345 : |1|10|100|1000", asString(NumericScale.makeLogScale(a, false, pad, 0.2, 3), a));
        Assert.assertEquals("log : 1 10,000 : |1|100|10,000", asString(NumericScale.makeLogScale(a, true, pad, 0.0, 3), a));
        Assert.assertEquals("log : 1 10,000 : |1|10|100|1000|10,000", asString(NumericScale.makeLogScale(a, true, pad, 0.0, 5), a));
        Assert.assertEquals("log : 1 5000 : |1|3|10|30|100|300|1000|3000", asString(NumericScale.makeLogScale(a, true, pad, 0.0, 10), a));

        a = Data.makeColumnField("a", "label", new Object[]{45, 120, 200, 3345});
        a = Data.toNumeric(a);
        Assert.assertEquals("log : 45 3345 : |100|1000", asString(NumericScale.makeLogScale(a, false, pad, 0.0, 3), a));
        Assert.assertEquals("log : 45 3345 : |100|1000", asString(NumericScale.makeLogScale(a, false, pad, 0.2, 3), a));
        Assert.assertEquals("log : 1 3345 : |1|10|100|1000", asString(NumericScale.makeLogScale(a, false, pad, 0.75, 3), a));
        Assert.assertEquals("log : 10 10,000 : |10|100|1000|10,000", asString(NumericScale.makeLogScale(a, true, pad, 0.0, 3), a));

        a = Data.makeColumnField("a", "label", new Object[]{0.04, 0.2, 5});
        a = Data.toNumeric(a);
        Assert.assertEquals("log : 0.04 5 : |0.1|1", asString(NumericScale.makeLogScale(a, false, pad, 0.0, 3), a));
        Assert.assertEquals("log : 0.04 5 : |0.1|1", asString(NumericScale.makeLogScale(a, false, pad, 0.2, 3), a));
        Assert.assertEquals("log : 0.04 5 : |0.1|1", asString(NumericScale.makeLogScale(a, false, pad, 0.75, 3), a));
        Assert.assertEquals("log : 0.01 10 : |0.01|0.1|1|10", asString(NumericScale.makeLogScale(a, true, pad, 0.0, 3), a));
    }

    private String asString(NumericScale details, Field field) {
        String s = details.type + " : " + field.format(details.min) + " " + field.format(details.max) + " : ";
        for (int i = 0; i < details.divisions.length; i++)
            s += "|" + field.format(details.divisions[i]);
        return s;
    }

}
