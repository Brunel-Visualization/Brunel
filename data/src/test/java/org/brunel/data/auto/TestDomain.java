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
import org.brunel.data.util.DateFormat;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class TestDomain {

	@Test
	public void emptyTest() {
		Domain domain = new Domain();
		assertEquals(0, domain.domains().length);
		assertEquals(0, domain.domainRanges().length);
	}

	@Test
	public void testOneCategorical() {
		Domain domain = new Domain();

		Field f = Fields.makeColumnField("field", null, new Object[]{"a", "c", "f"});

		domain.include(f);
		Object[][] domains = domain.domains();
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domains.length);
		assertEquals(1, ranges.length);

		assertEquals(3, domains[0].length);
		assertEquals("a", domains[0][0]);
		assertEquals("c", domains[0][1]);
		assertEquals("f", domains[0][2]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testOneNumeric() {
		Domain domain = new Domain();

		Field f = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{40, 3}));

		domain.include(f);
		Object[][] domains = domain.domains();
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domains.length);
		assertEquals(1, ranges.length);

		assertEquals(2, domains[0].length);
		assertEquals(3.0, domains[0][0]);
		assertEquals(40.0, domains[0][1]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testOneDate() {
		Domain domain = new Domain();

		// Make some dates
		Date d1 = Data.asDate("1971-1-3");
		Date d2 = Data.asDate("1971-1-9");
		Date d3 = Data.asDate("1971-1-31");

		Field f = Data.toDate(Fields.makeColumnField("field", null, new Object[]{d1, d2, d3}));

		domain.include(f);
		Object[][] domains = domain.domains();
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domains.length);
		assertEquals(1, ranges.length);

		assertEquals(2, domains[0].length);
		assertEquals("Jan 3, 1971", DateFormat.YearMonthDay.format((Date) domains[0][0]));
		assertEquals("Jan 31, 1971", DateFormat.YearMonthDay.format((Date) domains[0][1]));

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testTwoCategorical() {
		Domain domain = new Domain();

		Field f = Fields.makeColumnField("field", null, new Object[]{"a", "c", "f"});
		Field g = Fields.makeColumnField("field", null, new Object[]{"a", "d", "h"});

		domain.include(f);
		domain.include(g);
		Object[][] domains = domain.domains();
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domains.length);
		assertEquals(1, ranges.length);

		assertEquals(5, domains[0].length);
		assertEquals("a", domains[0][0]);
		assertEquals("c", domains[0][1]);
		assertEquals("f", domains[0][2]);
		assertEquals("d", domains[0][3]);
		assertEquals("h", domains[0][4]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testTwoNumeric() {
		Domain domain = new Domain();

		Field f = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{40, 3}));
		Field g = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{20, 50}));

		domain.include(f);
		domain.include(g);
		Object[][] domains = domain.domains();
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domains.length);
		assertEquals(1, ranges.length);

		assertEquals(2, domains[0].length);
		assertEquals(3.0, domains[0][0]);
		assertEquals(50.0, domains[0][1]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testMixedDomains() {
		Date d1 = Data.asDate("1971-1-3");
		Date d2 = Data.asDate("1971-1-9");
		Date d3 = Data.asDate("1971-1-31");
		Field d = Data.toDate(Fields.makeColumnField("field", null, new Object[]{d1, d2, d3}));

		Field n1 = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{40, 3}));
		Field n2 = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{20, 50}));

		Field c1 = Fields.makeColumnField("field", null, new Object[]{"a", "c", "f"});
		Field c2 = Fields.makeColumnField("field", null, new Object[]{"a", "d", "h"});

		// Include in mixed order
		Domain domain = new Domain();
		domain.include(n1);
		domain.include(c1);
		domain.include(d);
		domain.include(n2);
		domain.include(c2);

		Object[][] domains = domain.domains();
		double[][] ranges = domain.domainRanges();

		// Three separate spans
		assertEquals(3, domains.length);
		assertEquals(3, ranges.length);

		// First span: time
		assertEquals(2, domains[0].length);
		assertEquals("Jan 3, 1971", DateFormat.YearMonthDay.format((Date) domains[0][0]));
		assertEquals("Jan 31, 1971", DateFormat.YearMonthDay.format((Date) domains[0][1]));

		// Second span: numeric
		assertEquals(2, domains[1].length);
		assertEquals(3.0, domains[1][0]);
		assertEquals(50.0, domains[1][1]);

		// Third span: categorical
		assertEquals(5, domains[2].length);
		assertEquals("a", domains[2][0]);
		assertEquals("c", domains[2][1]);
		assertEquals("f", domains[2][2]);
		assertEquals("d", domains[2][3]);
		assertEquals("h", domains[2][4]);

		double R = 1.0 / (1 + 1 + 0.2 + 5.0 / 8);        // The size we expect for a numeric span

		// First numeric (date)
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(R, ranges[0][1], 1e-9);

		// Second numeric
		assertEquals(1.1 * R, ranges[1][0], 1e-9);
		assertEquals(2.1 * R, ranges[1][1], 1e-9);

		// Third: categorical
		assertEquals(2.2 * R, ranges[2][0], 1e-9);
		assertEquals(1.0, ranges[2][1], 1e-9);

	}

}
