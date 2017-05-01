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
import org.brunel.data.modify.Transform;
import org.brunel.data.util.DateFormat;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class TestDomain {

	@Test
	public void emptyTest() {
		Domain domain = new Domain(true);
		assertEquals(0, domain.spanCount());
		assertEquals(0, domain.domainRanges().length);
	}

	@Test
	public void testOneCategorical() {
		Domain domain = new Domain(true);

		Field f = Fields.makeColumnField("field", null, new Object[]{"a", "c", "f"});

		domain.include(f);
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domain.spanCount());
		assertEquals(1, ranges.length);

		Object[] content = domain.getSpan(0).content();
		assertEquals(3, content.length);
		assertEquals("a", content[0]);
		assertEquals("c", content[1]);
		assertEquals("f", content[2]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testOneNumeric() {
		Domain domain = new Domain(true);

		Field f = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{40, 3}));

		domain.include(f);
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domain.spanCount());
		assertEquals(1, ranges.length);

		Object[] content = domain.getSpan(0).content();
		assertEquals(2, content.length);
		assertEquals(3.0, content[0]);
		assertEquals(40.0, content[1]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testOneDate() {
		Domain domain = new Domain(true);

		// Make some dates
		Date d1 = Data.asDate("1971-1-3");
		Date d2 = Data.asDate("1971-1-9");
		Date d3 = Data.asDate("1971-1-31");

		Field f = Data.toDate(Fields.makeColumnField("field", null, new Object[]{d1, d2, d3}));

		domain.include(f);
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domain.spanCount());
		assertEquals(1, ranges.length);

		Object[] content = domain.getSpan(0).content();
		assertEquals(2, content.length);
		assertEquals("Jan 3, 1971", DateFormat.YearMonthDay.format((Date) content[0]));
		assertEquals("Jan 31, 1971", DateFormat.YearMonthDay.format((Date) content[1]));

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testTwoCategorical() {
		Domain domain = new Domain(true);

		Field f = Fields.makeColumnField("field", null, new Object[]{"a", "c", "f"});
		Field g = Fields.makeColumnField("field", null, new Object[]{"a", "d", "h"});

		domain.include(f);
		domain.include(g);
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domain.spanCount());
		assertEquals(1, ranges.length);

		Object[] content = domain.getSpan(0).content();
		assertEquals(5, content.length);
		assertEquals("a", content[0]);
		assertEquals("c", content[1]);
		assertEquals("f", content[2]);
		assertEquals("d", content[3]);
		assertEquals("h", content[4]);

		assertEquals(2, ranges[0].length);
		assertEquals(0.0, ranges[0][0], 1e-9);
		assertEquals(1.0, ranges[0][1], 1e-9);
	}

	@Test
	public void testTwoNumeric() {
		Domain domain = new Domain(true);

		Field f = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{40, 3}));
		Field g = Data.toNumeric(Fields.makeColumnField("field", null, new Object[]{20, 50}));

		domain.include(f);
		domain.include(g);
		double[][] ranges = domain.domainRanges();

		assertEquals(1, domain.spanCount());
		assertEquals(1, ranges.length);

		assertEquals(2, domain.getSpan(0).content().length);
		assertEquals(3.0, domain.getSpan(0).content()[0]);
		assertEquals(50.0, domain.getSpan(0).content()[1]);

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
		Domain domain = new Domain(true).include(n1).include(c1).include(d).include(n2).include(c2);

		double[][] ranges = domain.domainRanges();

		// Three separate spans
		assertEquals(3, domain.spanCount());
		assertEquals(3, ranges.length);

		// First span: time
		Object[] content1 = domain.getSpan(0).content();
		assertEquals(2, content1.length);
		assertEquals("Jan 3, 1971", DateFormat.YearMonthDay.format((Date) content1[0]));
		assertEquals("Jan 31, 1971", DateFormat.YearMonthDay.format((Date) content1[1]));

		// Second span: numeric
		Object[] content2 = domain.getSpan(1).content();
		assertEquals(2, content2.length);
		assertEquals(3.0, content2[0]);
		assertEquals(50.0, content2[1]);

		// Third span: categorical
		Object[] content3 = domain.getSpan(2).content();
		assertEquals(5, content3.length);
		assertEquals("a", content3[0]);
		assertEquals("c", content3[1]);
		assertEquals("f", content3[2]);
		assertEquals("d", content3[3]);
		assertEquals("h", content3[4]);

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

	@Test
	public void testBinnedAlone() {
		Field a = Data.toNumeric(Fields.makeColumnField("num", null, new Object[]{3, 4, 9, 20, 23, 30, 45}));
		Field binnedA = Transform.bin(a, 3);

		// Binning when we prefer categorical output
		Domain domain = new Domain(false).include(binnedA);
		assertEquals(1, domain.spanCount());
		assertEquals(3, domain.getSpan(0).content().length);
		assertEquals("0\u202620", Data.format(domain.getSpan(0).content()[0], false));
		assertEquals("20\u202640", Data.format(domain.getSpan(0).content()[1], false));
		assertEquals("40\u202660", Data.format(domain.getSpan(0).content()[2], false));

		domain = new Domain(true).include(binnedA);
		assertEquals(1, domain.spanCount());
		assertEquals(2, domain.getSpan(0).content().length);
		assertEquals(0.0, domain.getSpan(0).content()[0]);
		assertEquals(60.0, domain.getSpan(0).content()[1]);

	}

	@Test
	public void testBinned() {
		Field a = Data.toNumeric(Fields.makeColumnField("num", null, new Object[]{3, 4, 9, 20, 23, 30, 45}));
		Field b = Data.toNumeric(Fields.makeColumnField("numeric", null, new Object[]{-45, -15}));

		Field c = Fields.makeColumnField("cat", null, new Object[]{"a", "c", "f"});

		Field binnedA = Transform.bin(a, 3);

		// Binned with categorical data -- one set of categories, ranges first
		Domain domain = new Domain(true).include(c).include(binnedA);
		assertEquals(1, domain.spanCount());
		assertEquals(6, domain.getSpan(0).content().length);
		assertEquals("0\u202620", Data.format(domain.getSpan(0).content()[0], false));
		assertEquals("20\u202640", Data.format(domain.getSpan(0).content()[1], false));
		assertEquals("40\u202660", Data.format(domain.getSpan(0).content()[2], false));
		assertEquals("a", domain.getSpan(0).content()[3]);
		assertEquals("c", domain.getSpan(0).content()[4]);
		assertEquals("f", domain.getSpan(0).content()[5]);

		// Binned with numeric data -- one set of numeric range
		domain = new Domain(true).include(b).include(binnedA);
		assertEquals(1, domain.spanCount());
		assertEquals(2, domain.getSpan(0).content().length);
		assertEquals(-45.0, domain.getSpan(0).content()[0]);
		assertEquals(60.0, domain.getSpan(0).content()[1]);

	}

}
