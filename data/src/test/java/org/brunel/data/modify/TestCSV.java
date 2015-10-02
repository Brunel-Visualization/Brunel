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

package org.brunel.data.modify;

import org.brunel.data.CannedData;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestCSV {

    private static final String SIMPLE = "a,b,c\n1,a,b\n2,c,d";
    private static final String SIMPLE_TABS = "a\tb\tc\n1\ta\tb\n2\tc\td";
    private static final String NASTY =
            ",B,B\n" +
                    "1,hello,I'm happy\n\r" +
                    "\"\"\"yes\"\"\",,yes\r\n" +
                    "\"a\nb\",\"no\",\"\"\"\"\"\"\n" +
                    ",\"\",";

    @Test
    public void testBank() {
        Field[] fields = CSV.read(CannedData.bank);
        Assert.assertEquals(8, fields.length);
        Field gender = fields[0];
        Field jobtime = fields[6];
        assertEquals("gender", gender.name);
        assertEquals("jobtime", jobtime.name);

        assertEquals(null, gender.min());
        assertEquals(97, jobtime.min(), 0.01);
        assertEquals(98, jobtime.max(), 0.01);
        assertEquals(25, gender.rowCount());

        // gender,bdate,educ,jobcat,salary,salbegin,jobtime,minority\n"
        // Male,19027,15,Manager,57000,27000,98,No\n"

        Dataset data = Dataset.make(fields);
        assertEquals(false, data.fields[0].isNumeric());
        assertEquals(true, data.fields[1].isNumeric());
        assertEquals(true, data.fields[2].isNumeric());
        assertEquals(false, data.fields[3].isNumeric());
        assertEquals(true, data.fields[4].isNumeric());
        assertEquals(true, data.fields[5].isNumeric());
        assertEquals(true, data.fields[6].isNumeric());
        assertEquals(false, data.fields[7].isNumeric());

        assertEquals(2, data.fields[0].categories().length);
        assertEquals(25, data.fields[1].categories().length);
        assertEquals(4, data.fields[2].categories().length);
        assertEquals(2, data.fields[3].categories().length);
        assertEquals(23, data.fields[4].categories().length);
        assertEquals(16, data.fields[5].categories().length);
        assertEquals(2, data.fields[6].categories().length);
        assertEquals(2, data.fields[7].categories().length);

        Assert.assertEquals("Female, Male", Data.join(data.fields[0].categories()));
        assertEquals("8, 12, 15, 16", Data.join(data.fields[2].categories()));
        assertEquals("Clerical, Manager", Data.join(data.fields[3].categories()));
        assertEquals("97, 98", Data.join(data.fields[6].categories()));
        assertEquals("No, Yes", Data.join(data.fields[7].categories()));
    }

    @Test
    public void testIdentifier() {
        assertEquals("_", CSV.identifier(""));
        assertEquals("a", CSV.identifier("a"));
        assertEquals("name", CSV.identifier("name"));
        assertEquals("n1ME", CSV.identifier("n1ME"));
        assertEquals("f_100", CSV.identifier("f_100"));
        assertEquals("hello_all", CSV.identifier("hello all"));
        assertEquals("_123", CSV.identifier("123"));
        assertEquals("yo_1_2_3", CSV.identifier("yo 1 2 3"));
        assertEquals("ff0", CSV.identifier("ff0(?a,b)?"));

    }

    @Test
    public void testNastyAutoConversion() {
        Dataset data = Dataset.make(CSV.read(NASTY));
        assertEquals(6, data.fields.length);
        assertEquals("_", data.fields[0].name);
        assertEquals("B", data.fields[1].name);
        assertEquals("B_1", data.fields[2].name);
        assertEquals("#count", data.fields[3].name);
        assertEquals("#row", data.fields[4].name);
        assertEquals("#selection", data.fields[5].name);

        assertEquals("", data.fields[0].label);
        assertEquals("B", data.fields[1].label);
        assertEquals("B", data.fields[2].label);
        assertEquals("Count", data.fields[3].label);
        assertEquals("Row", data.fields[4].label);
        assertEquals("Selection", data.fields[5].label);
    }

    @Test
    public void testReadableLabels() {
        assertEquals("", CSV.readable(""));
        assertEquals("A", CSV.readable("a"));
        assertEquals("Testing", CSV.readable("testing"));
        assertEquals("My Name", CSV.readable("my name"));
        assertEquals("My Name Is 12", CSV.readable("MyNameIs12"));

    }

    @Test
    public void testSimpleFields() {
        Field[] fields = CSV.read(SIMPLE);
        Assert.assertEquals(3, fields.length);
        assertEquals("a", fields[0].name);
        assertEquals("b", fields[1].name);
        assertEquals("c", fields[2].name);
        assertEquals("A", fields[0].label);
        assertEquals("B", fields[1].label);
        assertEquals("C", fields[2].label);

        assertEquals("1", fields[0].value(0));
        assertEquals("c", fields[1].value(1));
    }

    @Test
    public void testSimpleFieldsFromTabs() {
        Field[] fields = CSV.read(SIMPLE_TABS);
        Assert.assertEquals(3, fields.length);
        assertEquals("a", fields[0].name);
        assertEquals("b", fields[1].name);
        assertEquals("c", fields[2].name);
        assertEquals("A", fields[0].label);
        assertEquals("B", fields[1].label);
        assertEquals("C", fields[2].label);

        assertEquals("1", fields[0].value(0));
        assertEquals("c", fields[1].value(1));
    }

    @Test
    public void testSimpleReading() {
        Object[][] data = CSV.parse(SIMPLE);
        Assert.assertEquals(3, data.length);
        Assert.assertEquals(3, data[0].length);
        Assert.assertEquals("a", data[0][0]);
        Assert.assertEquals("b", data[0][1]);
        Assert.assertEquals("c", data[0][2]);
        Assert.assertEquals(3, data[1].length);
        Assert.assertEquals("1", data[1][0]);
        Assert.assertEquals("a", data[1][1]);
        Assert.assertEquals("b", data[1][2]);
        Assert.assertEquals(3, data[2].length);
        Assert.assertEquals("2", data[2][0]);
        Assert.assertEquals("c", data[2][1]);
        Assert.assertEquals("d", data[2][2]);
    }

}
