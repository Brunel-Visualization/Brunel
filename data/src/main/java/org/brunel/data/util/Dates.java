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

package org.brunel.data.util;

import org.brunel.data.Data;
import org.brunel.translator.JSTranslation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@JSTranslation(ignore = true)
public class Dates {

	// Keep 100 known mappings
	private static final Map<String, Date> parsed = new LinkedHashMap<String, Date>() {
		protected boolean removeEldestEntry(Map.Entry<String, Date> eldest) {
			return size() > 1000;
		}
	};

	private static final List<SimpleDateFormat> dateFormats;
	private static final SimpleDateFormat[] outputFormats, canonicalFormats;

	public static String format(Date date, DateFormat dateFormat) {
		synchronized (outputFormats) {
			return outputFormats[dateFormat.ordinal()].format(date);
		}
	}

	public static String formatCanonical(Date date, DateFormat dateFormat) {
		synchronized (canonicalFormats) {
			return canonicalFormats[dateFormat.ordinal()].format(date);
		}
	}

	public static Date parse(Object c) {
		synchronized (dateFormats) {
			if (c == null || c instanceof Date) return (Date) c;
			if (c instanceof Number) return new Date(Math.round(((Number) c).doubleValue() * Data.MILLIS_PER_DAY));
			String s = c.toString().trim();
			if (s.isEmpty()) return null;
			if (parsed.containsKey(s)) return parsed.get(s);

			Date result = null;
			for (SimpleDateFormat f : dateFormats)
				try {
					result = f.parse(s);
					break;
				} catch (ParseException ignored) {
					// Unsuccessful; keep trying
				}
			parsed.put(s, result);
			return result;
		}
	}

	static {
		outputFormats = new SimpleDateFormat[]{
				new SimpleDateFormat("HH:mm:ss"),            	// seconds
				new SimpleDateFormat("HH:mm"),              	// hours and minutes
				new SimpleDateFormat("MMM d HH:mm"),        	// day and hour
				new SimpleDateFormat("MMM d, yyyy"),        	// full date
				new SimpleDateFormat("MMM yyyy"),            	// months
				new SimpleDateFormat("yyyy")                	// years
		};

		canonicalFormats = new SimpleDateFormat[]{
				new SimpleDateFormat("HH:mm:ss"),            	// seconds
				new SimpleDateFormat("HH:mm:ss"),               // hours and minutes
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),    // day and hour
				new SimpleDateFormat("yyyy-MM-dd"),        		// full date
				new SimpleDateFormat("yyyy-MM-dd"),            	// months
				new SimpleDateFormat("yyyy-MM-dd")              // years
		};


		dateFormats = new LinkedList<>();
		dateFormats.add(new SimpleDateFormat("y-M-d'T'H:m:s.SSS", Locale.US));
		dateFormats.add(new SimpleDateFormat("y-M-d'T'H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("y-M-d'T'H:m", Locale.US));
		dateFormats.add(new SimpleDateFormat("MMM d, yyyy H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US));
		dateFormats.add(new SimpleDateFormat("y-MM-dd", Locale.US));
		dateFormats.add(new SimpleDateFormat("d-MMM-y", Locale.US));
		dateFormats.add(new SimpleDateFormat("MMM d, yyyy", Locale.US));
		dateFormats.add(new SimpleDateFormat("M/d/y H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("d/M/y H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("M-d-y H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("M/d/y H:m", Locale.US));
		dateFormats.add(new SimpleDateFormat("d-M-y H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("d/M/y H:m", Locale.US));
		dateFormats.add(new SimpleDateFormat("M-d-y H:m", Locale.US));
		dateFormats.add(new SimpleDateFormat("d-M-y H:m", Locale.US));
		dateFormats.add(new SimpleDateFormat("M/d/y", Locale.US));
		dateFormats.add(new SimpleDateFormat("d/M/y", Locale.US));
		dateFormats.add(new SimpleDateFormat("MMM-y", Locale.US));
		dateFormats.add(new SimpleDateFormat("MMM d", Locale.US));
		dateFormats.add(new SimpleDateFormat("d-M-y", Locale.US));
		dateFormats.add(new SimpleDateFormat("M/d", Locale.US));
		dateFormats.add(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US));
		dateFormats.add(new SimpleDateFormat("H:m:s", Locale.US));
		dateFormats.add(new SimpleDateFormat("H:m", Locale.US));

		for (SimpleDateFormat df : outputFormats)
			df.setTimeZone(TimeZone.getTimeZone("UTC"));
		for (SimpleDateFormat df : canonicalFormats)
			df.setTimeZone(TimeZone.getTimeZone("UTC"));
		for (SimpleDateFormat format : dateFormats)
			format.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

}
