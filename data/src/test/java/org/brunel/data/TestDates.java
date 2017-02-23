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

import org.brunel.data.util.DateFormat;
import org.brunel.data.util.DateUnit;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class TestDates {

	@Test
	public void testDateFloor() {
		Date d = Data.asDate("February 19, 1976 3:04:25");
		assertEquals(Data.asDate("February 19, 1976 3:04:25"), DateUnit.floor(d, DateUnit.second, 1));
		assertEquals(Data.asDate("February 19, 1976 3:04:24"), DateUnit.floor(d, DateUnit.second, 2));
		assertEquals(Data.asDate("February 19, 1976 3:04:15"), DateUnit.floor(d, DateUnit.second, 15));
		assertEquals(Data.asDate("February 19, 1976 3:03:00"), DateUnit.floor(d, DateUnit.minute, 3));
		assertEquals(Data.asDate("February 19, 1976 3:00:00"), DateUnit.floor(d, DateUnit.minute, 10));
		assertEquals(Data.asDate("February 19, 1976 3:00:00"), DateUnit.floor(d, DateUnit.hour, 1));
		assertEquals(Data.asDate("February 19, 1976 3:00:00"), DateUnit.floor(d, DateUnit.hour, 3));
		assertEquals(Data.asDate("February 19, 1976 0:00:00"), DateUnit.floor(d, DateUnit.day, 1));
		assertEquals(Data.asDate("February 15, 1976 0:00:00"), DateUnit.floor(d, DateUnit.day, 7));
		assertEquals(Data.asDate("February 1, 1976 0:00:00"), DateUnit.floor(d, DateUnit.day, 20));
		assertEquals(Data.asDate("February 15, 1976 0:00:00"), DateUnit.floor(d, DateUnit.week, 1));
		assertEquals(Data.asDate("February 15, 1976 0:00:00"), DateUnit.floor(d, DateUnit.week, 2));
		assertEquals(Data.asDate("February 1, 1976 0:00:00"), DateUnit.floor(d, DateUnit.month, 1));
		assertEquals(Data.asDate("January 1, 1976 0:00:00"), DateUnit.floor(d, DateUnit.month, 4));
		assertEquals(Data.asDate("January 1, 1976 0:00:00"), DateUnit.floor(d, DateUnit.quarter, 1));
		assertEquals(Data.asDate("January 1, 1976 0:00:00"), DateUnit.floor(d, DateUnit.year, 1));
		assertEquals(Data.asDate("January 1, 1975 0:00:00"), DateUnit.floor(d, DateUnit.year, 5));
		assertEquals(Data.asDate("January 1, 1970 0:00:00"), DateUnit.floor(d, DateUnit.decade, 1));
		assertEquals(Data.asDate("January 1, 1960 0:00:00"), DateUnit.floor(d, DateUnit.decade, 2));
		assertEquals(Data.asDate("January 1, 1900 0:00:00"), DateUnit.floor(d, DateUnit.century, 1));
		assertEquals(Data.asDate("January 1, 1500 0:00:00"), DateUnit.floor(d, DateUnit.century, 5));
	}

	@Test
	public void testDateFormats() {
		Date d = Data.asDate("January 19, 2011 3:04:05");
		assertEquals("03:04:05", DateFormat.HourMinSec.format(d));
		assertEquals("03:04", DateFormat.HourMin.format(d));
		assertEquals("Jan 19 03:04", DateFormat.DayHour.format(d));
		assertEquals("Jan 19, 2011", DateFormat.YearMonthDay.format(d));
		assertEquals("Jan 2011", DateFormat.YearMonth.format(d));
		assertEquals("2011", DateFormat.Year.format(d));
	}

	@Test
	public void testCanonicalDateFormats() {
		Date d = Data.asDate("January 19, 2011 03:04:05");
		assertEquals("03:04:05", DateFormat.HourMinSec.formatCanonical(d));
		assertEquals("03:04:05", DateFormat.HourMin.formatCanonical(d));
		assertEquals("2011-01-19 03:04:05", DateFormat.DayHour.formatCanonical(d));
		assertEquals("2011-01-19", DateFormat.YearMonthDay.formatCanonical(d));
		assertEquals("2011-01-19", DateFormat.YearMonth.formatCanonical(d));
		assertEquals("2011-01-19", DateFormat.Year.formatCanonical(d));
	}

	@Test
	public void testCanonicalRoundTrip() {
		assertEquals("03:04:05", DateFormat.HourMinSec.formatCanonical(Data.asDate("03:04:05")));
		assertEquals("03:04:05", DateFormat.HourMin.formatCanonical(Data.asDate("03:04:05")));
		assertEquals("2011-01-19 03:04:05", DateFormat.DayHour.formatCanonical(Data.asDate("2011-01-19 03:04:05")));
		assertEquals("2011-01-19", DateFormat.YearMonthDay.formatCanonical(Data.asDate("2011-01-19")));
		assertEquals("2011-01-19", DateFormat.YearMonth.formatCanonical(Data.asDate("2011-01-19")));
		assertEquals("2011-01-19", DateFormat.Year.formatCanonical(Data.asDate("2011-01-19")));
	}



	@Test
	public void testDateCalculations() {
		Date d = Data.asDate("January 1, 1970");
		assertEquals(0, Data.asNumeric(d), 1e-6);

		d = Data.asDate("1/1/88");
		assertEquals(6574, Data.asNumeric(d), 1e-6);
	}

	@Test
	public void testDateIncrement() {
		Date d = Data.asDate("February 19, 1976 3:04:25");
		assertEquals(Data.asDate("February 19, 1976 3:04:26"), DateUnit.increment(d, DateUnit.second, 1));
		assertEquals(Data.asDate("February 19, 1976 3:04:19"), DateUnit.increment(d, DateUnit.second, -6));
		assertEquals(Data.asDate("February 19, 1976 4:04:25"), DateUnit.increment(d, DateUnit.second, 3600));
		assertEquals(Data.asDate("February 19, 1976 4:34:25"), DateUnit.increment(d, DateUnit.minute, 90));
		assertEquals(Data.asDate("February 29, 1976 4:04:25"), DateUnit.increment(d, DateUnit.hour, 241));
		assertEquals(Data.asDate("March 1, 1976 3:04:25"), DateUnit.increment(d, DateUnit.day, 11));
		assertEquals(Data.asDate("February 5, 1976 3:04:25"), DateUnit.increment(d, DateUnit.week, -2));
		assertEquals(Data.asDate("March 19, 1977 3:04:25"), DateUnit.increment(d, DateUnit.month, 13));
		assertEquals(Data.asDate("February 19, 1975 3:04:25"), DateUnit.increment(d, DateUnit.year, -1));
		assertEquals(Data.asDate("February 19, 1956 3:04:25"), DateUnit.increment(d, DateUnit.decade, -2));
		assertEquals(Data.asDate("February 19, 2276 3:04:25"), DateUnit.increment(d, DateUnit.century, 3));
	}

	@Test
	public void testDateParseSpeed() {
		long t0 = System.currentTimeMillis();
		int N = 1000;

		int check = 0;
		for (int i = 0; i < N; i++) {
			Date d = Data.asDate(1900 + (i % 100));
			if (d != null) check++;
		}
		assertEquals(N, check);

		long t1 = System.currentTimeMillis();
		assertEquals(true, t1 - t0 < 100);
	}

	@Test
	public void testEnum() {
		assertEquals(10, DateUnit.values().length);
		assertEquals("decade", DateUnit.decade.toString());
		assertEquals("month", DateUnit.month.toString());
		assertEquals("second", DateUnit.second.toString());
		assertEquals(1, DateUnit.decade.ordinal());
		assertEquals(4, DateUnit.month.ordinal());
		assertEquals(9, DateUnit.second.ordinal());
	}

}
