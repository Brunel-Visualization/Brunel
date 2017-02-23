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

import org.brunel.data.CannedData;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.auto.Auto;
import org.brunel.data.io.CSV;
import org.brunel.data.util.Range;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestBin {

	// gender,bdate,educ,jobcat,salary,salbegin,jobtime,minority\n"
	// Male,19027,15,Manager,57000,27000,98,No\n"
	private final Dataset data = Dataset.make(CSV.read(CannedData.bank));
	private final Dataset data1 = Dataset.make(CSV.read(CannedData.movies));

	@Test
	public void testBinningDate() {
		// 'bdate' is an excel-style date, ranging from 1930's to 1970's
		Field field = Data.toDate(data.fields[1], "excel");
		assertEquals(true, field.isNumeric());
		assertEquals(true, field.isDate());

		Field binned;

		binned = Transform.bin(field, 4);
		assertEquals("1930|1940|1950|1960|1970|1980", binsToString(binned));
		binned = Transform.bin(field, 8);
		assertEquals("1930|1935|1940|1945|1950|1955|1960|1965|1970|1975", binsToString(binned));
		binned = Transform.bin(field, 2);
		assertEquals("1920|1940|1960|1980", binsToString(binned));
		binned = Transform.bin(field, 20);
		assertEquals("1932|1934|1936|1938|1940|1942|1944|1946|1948|1950|1952|1954|1956|1958|1960|1962|1964|1966 ...",
				binsToString(binned));
		binned = Transform.bin(field, 100);
		assertEquals(
				"Jul 1933|Oct 1933|Jan 1934|Apr 1934|Jul 1934|Oct 1934|Jan 1935|Apr 1935|Jul 1935|Oct 1935|Jan 1936|Apr 1936|Jul 1936|Oct 1936|Jan 1937|Apr 1937|Jul 1937|Oct 1937 ...",
				binsToString(binned));

	}

	@Test
	public void testBinLog() {
		Field f = Auto.convert(Fields.makeColumnField("a", null, new Object[]{3, 4, 5, 14, 201, 1003}));
		f.set("transform", "log");
		Field binned;

		binned = Transform.bin(f, 4);
		assertEquals("1|10|100|1000|10,000", binsToString(binned));

		binned = Transform.bin(f, 2);
		assertEquals("1|100|10,000", binsToString(binned));

		binned = Transform.bin(f, 8);
		assertEquals("1|3|10|30|100|300|1000|3000", binsToString(binned));

	}

	private String binsToString(Field f) {
		String s = "";
		Object[] categories = f.categories();
		for (int i = 0; i < categories.length; i++) {
			Object o = categories[i];
			if (o instanceof Range) {
				Range r = (Range) o;
				if (i == 0) s += f.format(r.low);
				s += "|";
				s += f.format(r.high);
			} else {
				s += o + "|";
			}
			if (i > 15) {
				s += " ...";
				break;
			}
		}
		return s;
	}

	@Test
	public void testBinningNominal() {
		Field base = data1.fields[10];
		Field binned = Transform.bin(base, 3);
		assertEquals("Contemporary Fiction|Science Fiction|\u2026|", binsToString(binned));
		binned = Transform.bin(base, 4);
		assertEquals("Contemporary Fiction|Historical Fiction|Science Fiction|Super Hero|", binsToString(binned));
		binned = Transform.bin(base, -1);
		assertEquals(base, binned);
	}

	@Test
	public void testBinningNumeric() {
		assertEquals(true, data.fields[1].isNumeric());
		assertEquals(false, data.fields[1].isDate());

		Field binned;

		binned = Transform.bin(data.fields[1], 4);
		assertEquals("10,000|15,000|20,000|25,000", binsToString(binned));
		binned = Transform.bin(data.fields[1], 8);
		assertEquals("10,000|12,000|14,000|16,000|18,000|20,000|22,000|24,000|26,000", binsToString(binned));
		binned = Transform.bin(data.fields[1], 2);
		assertEquals("10,000|20,000|30,000", binsToString(binned));
		binned = Transform.bin(data.fields[1], 20);
		assertEquals(
				"10,000|11,000|12,000|13,000|14,000|15,000|16,000|17,000|18,000|19,000|20,000|21,000|22,000|23,000|24,000|25,000",
				binsToString(binned));

	}

	@Test
	public void testBinningNumericGranular() {
		Field binned;

		// 'educ' is in the range 8 .. 16
		binned = Transform.bin(data.fields[2], 4);
		assertEquals("8|10|12|14|16", binsToString(binned));
		binned = Transform.bin(data.fields[2], 8);
		assertEquals("7.5|8.5|9.5|10.5|11.5|12.5|13.5|14.5|15.5|16.5", binsToString(binned));
		binned = Transform.bin(data.fields[2], 2);
		assertEquals("0|10|20", binsToString(binned));
		binned = Transform.bin(data.fields[2], 20);
		assertEquals("8|8.5|9|9.5|10|10.5|11|11.5|12|12.5|13|13.5|14|14.5|15|15.5|16", binsToString(binned));
	}

}
