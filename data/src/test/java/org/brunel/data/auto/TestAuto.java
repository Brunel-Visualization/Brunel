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

package org.brunel.data.auto;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.util.ItemsList;
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

        Field a = Fields.makeColumnField("field", null, new Object[]{d1, d2, d3, null});
        Field b = Auto.convert(a);
        assertEquals(true, b.isNumeric());
        assertEquals(true, b.isDate());

        // Should ignore one error
        a = Fields.makeColumnField("field", null, new Object[]{d1, d2, "oops", d3, null, null});
        b = Auto.convert(a);
        assertEquals(true, b.isNumeric());
        assertEquals(true, b.isDate());
        assertEquals(3, b.valid());

        a = Fields.makeColumnField("field", null, new Object[]{"1971-1-3", "1971-1-12"});
        b = Auto.convert(a);
        assertEquals(true, b.isNumeric());
        assertEquals(true, b.isDate());
        assertEquals(2, b.valid());
        assertEquals(9.0, b.max() - b.min(), 0.001);

        a = Fields.makeColumnField("field", null, new Object[]{1970, 1972, 1978});
        b = Auto.convert(a);
        assertEquals(true, b.isNumeric());
        assertEquals(true, b.isDate());
        assertEquals(3, b.valid());
        assertEquals(8 * 365 + 2, b.max() - b.min(), 0.001);

        a = Fields.makeColumnField("field", null, new Object[]{1970, 1971.5, 1978});
        b = Auto.convert(a);
        assertEquals(true, b.isNumeric());
        assertEquals(false, b.isDate());
        assertEquals(3, b.valid());
        assertEquals(8, b.max() - b.min(), 0.001);

    }

    @Test
    public void testAutoConvertNumeric() {
        Field a = Fields.makeColumnField("field", null, new Object[]{1, 2, 3, 4});
        Field b = Auto.convert(a);
        assertEquals(true, b.isNumeric());
        assertEquals(false, b.isDate());
        assertEquals(2.5, b.numProperty("mean"), 0.001);

        a = Fields.makeColumnField("field", null, new Object[]{"1", "2", "3", "4"});
        b = Auto.convert(a);
        assertEquals(false, a == b);
        assertEquals(true, b.isNumeric());
        assertEquals(false, b.isDate());
        assertEquals(2.5, b.numProperty("mean"), 0.001);

        a = Fields.makeColumnField("field", null, new Object[]{"a", "2", "3", "4"});
        b = Auto.convert(a);
        assertEquals(false, a == b);
        assertEquals(true, b.isNumeric());
        assertEquals(false, b.isDate());
        assertEquals(3.0, b.numProperty("mean"), 0.001);

        a = Fields.makeColumnField("field", null, new Object[]{"a", "2", "c", "4"});
        b = Auto.convert(a);
        assertEquals(true, a == b);
        assertEquals(false, b.isNumeric());
        assertEquals(false, b.isDate());
    }

    @Test
    public void testAutoConvertLists() {
        // TODO: Make work in JS
        Field a = Fields.makeColumnField("field", null, new Object[]{"a,b,c", "c,a", null, "a ,e", ""});
        Field b = Auto.convert(a);
        assertEquals(false, a == b);
        assertEquals(false, b.isNumeric());
        assertEquals(false, b.isDate());
        assertEquals(new ItemsList(new String[]{"a", "b", "c"}), b.value(0));
        assertEquals(new ItemsList(new String[]{"c", "a"}), b.value(1));
        assertEquals(null, b.value(2));
        assertEquals(new ItemsList(new String[]{"a", "e"}), b.value(3));
        assertEquals(new ItemsList(new String[]{}), b.value(4));

    }

    @Test
    public void testChooseTransform() {
        Field a = Fields.makeColumnField("a", "label", new Object[]{100, 200, 300, 400, 500, 600});
        Field b = Fields.makeColumnField("b", "label", new Object[]{"a", "b", "c"});
        Field c = Fields.makeColumnField("d", "label", new Object[]{1, 2, 2, 2, 2, 2, 2, 3});
        Field d = Fields.makeColumnField("d", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10});
        Field e = Fields.makeColumnField("e", "label", new Object[]{1, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field f = Fields.makeColumnField("f", "label", new Object[]{0, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});
        Field g = Fields.makeColumnField("g", "label", new Object[]{-1, 1, 1, 1, 1, 2, 2, 2, 5, 10, 100, 1000});

        Auto.defineTransform(a);
        Auto.defineTransform(b);
        Auto.defineTransform(c);
        Auto.defineTransform(d);
        Auto.defineTransform(e);
        Auto.defineTransform(f);
        Auto.defineTransform(g);

        assertEquals("linear", a.strProperty("transform"));
        assertEquals("linear", b.strProperty("transform"));
        assertEquals("linear", c.strProperty("transform"));
        assertEquals("root", d.strProperty("transform"));
        assertEquals("log", e.strProperty("transform"));
        assertEquals("root", f.strProperty("transform"));
        assertEquals("linear", g.strProperty("transform"));
    }

    @Test
    public void testDateScaleDaysPlusRange() {
        double[] pad = {0, 0};

        Field a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 9, 2010"}));
        assertEquals("date : 2000 2020 : |2005|2010|2015|2020",
                asString(NumericScale.makeDateScale(spanOf(a), false, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 10, 2010"}));
        assertEquals("date : 00:00 00:00 : |00:00|06:00|12:00|18:00|00:00",
                asString(NumericScale.makeDateScale(spanOf(a), false, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 14, 2010"}));
        assertEquals(
                "date : Jan 9, 2010 Jan 14, 2010 : |Jan 9, 2010|Jan 10, 2010|Jan 11, 2010|Jan 12, 2010|Jan 13, 2010|Jan 14, 2010",
                asString(NumericScale.makeDateScale(spanOf(a), false, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "January 14, 2010"}));
        assertEquals(
                "date : Jan 9, 2010 Jan 14, 2010 : |Jan 9, 2010|Jan 10, 2010|Jan 11, 2010|Jan 12, 2010|Jan 13, 2010|Jan 14, 2010",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2010"}));
        assertEquals("date : Jan 2010 Aug 2010 : |Mar 2010|May 2010|Jul 2010",
                asString(NumericScale.makeDateScale(spanOf(a), false, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2010"}));
        assertEquals("date : Jan 2010 Sep 2010 : |Jan 2010|Mar 2010|May 2010|Jul 2010|Sep 2010",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2011"}));
        assertEquals("date : Jan 2010 Jan 2012 : |Jan 2010|Jul 2010|Jan 2011|Jul 2011|Jan 2012",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2014"}));
        assertEquals("date : Jan 2010 Jan 2015 : |Jan 2010|Jan 2011|Jan 2012|Jan 2013|Jan 2014|Jan 2015",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2024"}));
        assertEquals("date : 2010 2025 : |2010|2015|2020|2025",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 2124"}));
        assertEquals("date : 2000 2140 : |2000|2020|2040|2060|2080|2100|2120|2140",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"January 9, 2010", "August 9, 6124"}));
        assertEquals("date : 2000 6500 : |2000|2500|3000|3500|4000|4500|5000|5500|6000|6500",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

    }

    private NumericExtentDetail spanOf(Field a) {
        return NumericExtentDetail.makeForField(a);
    }

    @Test
    public void testDateScaleHoursOrLess() {
        Field a;
        double[] pad = {0, 0};

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:24"}));
        assertEquals("date : 09:45:22 09:45:24 : |09:45:22|09:45:23|09:45:24",
                asString(NumericScale.makeDateScale(spanOf(a), false, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:54"}));
        assertEquals(0.40650462963, a.min(), 1e-9);
        assertEquals(0.406875, a.max(), 1e-9);
        assertEquals("date : 09:45:22 09:45:54 : |09:45:30|09:45:40|09:45:50",
                asString(NumericScale.makeDateScale(spanOf(a), false, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "09:45:54"}));
        assertEquals("date : 09:45:20 09:46:00 : |09:45:20|09:45:30|09:45:40|09:45:50|09:46:00",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "10:01:54"}));
        assertEquals("date : 09:45 10:05 : |09:45|09:50|09:55|10:00|10:05",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "12:01:54"}));
        assertEquals("date : 09:30 12:30 : |09:30|10:00|10:30|11:00|11:30|12:00|12:30",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

        a = Data.toDate(Fields.makeColumnField("a", "label", new Object[]{"09:45:22", "23:01:54"}));
        assertEquals("date : 09:00 00:00 : |09:00|12:00|15:00|18:00|21:00|00:00",
                asString(NumericScale.makeDateScale(spanOf(a), true, pad, 5), a));

    }

    @Test
    public void testPadding() {
        Field a = Data.toNumeric(Fields.makeColumnField("a", "label", new Object[]{100, 200, 120, 200, 3100}));
        a.set("transform", "linear");
        assertEquals("linear : 70 3,130 : |500|1,000|1,500|2,000|2,500|3,000",
                asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, new double[]{0.01, 0.01}, 5, false), a));

        // Does not pad past zero
        assertEquals("linear : -200 3,400 : |0|1,000|2,000|3,000",
                asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, new double[]{0.1, 0.1}, 5, false), a));
    }

    @Test
    public void testLinearScale() {
        double[] pad = {0, 0};

        Field a = Fields.makeColumnField("a", "label", new Object[]{2, 4, 7, 120, 45, 120, 200, 3345});
        a = Data.toNumeric(a);
        a.set("transform", "linear");
        assertEquals("linear : 2 3,345 : |500|1,000|1,500|2,000|2,500|3,000",
                asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, pad, 5, false), a));
        assertEquals("linear : 2 3,345 : |200|400|600|800|1,000|1,200|1,400|1,600|1,800|2,000|2,200|2,400|2,600|2,800|3,000|3,200",
                asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, pad, 15, false), a));
        assertEquals("linear : 2 3,345 : |1,000|2,000|3,000", asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, pad, 2, false), a));
        assertEquals("linear : 2 3,345 : |2,000", asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, pad, 1, false), a));
        assertEquals("linear : 0 3,345 : |0|500|1,000|1,500|2,000|2,500|3,000",
                asString(NumericScale.makeLinearScale(spanOf(a), false, 0.01, pad, 7, false), a));
        assertEquals("linear : 0 3,500 : |0|500|1,000|1,500|2,000|2,500|3,000|3,500",
                asString(NumericScale.makeLinearScale(spanOf(a), true, 0.0, pad, 7, false), a));

        a = Fields.makeColumnField("a", "label", new Object[]{-22.2, -22.201, -22.9, -22.7});
        a = Data.toNumeric(a);
        assertEquals("linear : -23 -22 : |-23|-22.8|-22.6|-22.4|-22.2|-22",
                asString(Auto.makeNumericScale(NumericExtentDetail.makeForField(a), true, pad, 0.0, 5, false), a));
        assertEquals("linear : -22.9 -22.2 : |-22.8|-22.6|-22.4|-22.2",
                asString(NumericScale.makeLinearScale(spanOf(a), false, 0.0, pad, 4, false), a));
        assertEquals("linear : -23 -22 : |-23|-22.5|-22",
                asString(Auto.makeNumericScale(NumericExtentDetail.makeForField(a), true, pad, 0.0, 4, false), a));
        assertEquals("linear : -23 -22 : |-23|-22.5|-22",
                asString(Auto.makeNumericScale(NumericExtentDetail.makeForField(a), true, pad, 0.95, 4, false), a));
        assertEquals("linear : -30 0 : |-30|-20|-10|0", asString(NumericScale.makeLinearScale(spanOf(a), true, 0.99, pad, 4, false), a));


        a = Fields.makeColumnField("a", "label", new Object[]{0.0});
        a = Data.toNumeric(a);
        Auto.defineTransform(a);
        assertEquals("linear : 0 1 : |0|0.5|1", asString(NumericScale.makeLinearScale(spanOf(a), true, 0.99, pad, 4, false), a));
    }


    @Test
    public void testLogScale() {
        Field a = Fields.makeColumnField("a", "label", new Object[]{2, 4, 7, 120, 45, 120, 200, 3345});
        a = Data.toNumeric(a);
        double[] pad = {0, 0};
        assertEquals("log : 2 3,345 : |10|100|1,000", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.0, 3), a));
        assertEquals("log : 1 3,345 : |1|10|100|1,000", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.2, 3), a));
        assertEquals("log : 1 10,000 : |1|100|10,000", asString(NumericScale.makeLogScale(spanOf(a), true, pad, 0.0, 3), a));
        assertEquals("log : 1 10,000 : |1|10|100|1,000|10,000", asString(NumericScale.makeLogScale(spanOf(a), true, pad, 0.0, 5), a));
        assertEquals("log : 1 5,000 : |1|3|10|30|100|300|1,000|3,000", asString(NumericScale.makeLogScale(spanOf(a), true, pad, 0.0, 10), a));

        a = Fields.makeColumnField("a", "label", new Object[]{45, 120, 200, 3345});
        a = Data.toNumeric(a);
        assertEquals("log : 45 3,345 : |100|1,000", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.0, 3), a));
        assertEquals("log : 45 3,345 : |100|1,000", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.2, 3), a));
        assertEquals("log : 1 3,345 : |1|10|100|1,000", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.75, 3), a));
        assertEquals("log : 10 10,000 : |10|100|1,000|10,000", asString(NumericScale.makeLogScale(spanOf(a), true, pad, 0.0, 3), a));

        a = Fields.makeColumnField("a", "label", new Object[]{0.04, 0.2, 5});
        a = Data.toNumeric(a);
        assertEquals("log : 0.04 5 : |0.1|1", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.0, 3), a));
        assertEquals("log : 0.04 5 : |0.1|1", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.2, 3), a));
        assertEquals("log : 0.04 5 : |0.1|1", asString(NumericScale.makeLogScale(spanOf(a), false, pad, 0.75, 3), a));
        assertEquals("log : 0.01 10 : |0.01|0.1|1|10", asString(NumericScale.makeLogScale(spanOf(a), true, pad, 0.0, 3), a));
    }

    private String asString(NumericScale details, Field field) {
        String s = details.type + " : " + field.format(details.min) + " " + field.format(details.max) + " : ";
        for (int i = 0; i < details.divisions.length; i++)
            s += "|" + field.format(details.divisions[i]);
        return s;
    }

}
