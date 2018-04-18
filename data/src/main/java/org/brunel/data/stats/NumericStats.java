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

package org.brunel.data.stats;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.util.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NumericStats {

	public static void populate(Field f) {
		int n = f.rowCount();

		// Extract valid numeric data
		List<Double> valid = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Object item = f.value(i);
			if (item != null) {
				if (item instanceof Range) {
					Object low = ((Range) item).low;
					Object high = ((Range) item).high;
					valid.add(Data.asNumeric(low));
					valid.add(Data.asNumeric(high));
				} else {
					Double d = Data.asNumeric(item);
					if (d != null) valid.add(d);
				}
			}
		}
		Double[] data = valid.toArray(new Double[valid.size()]);

		n = data.length;
		f.set("validNumeric", n);

		// No numeric data -- give up and go home
		if (n == 0) return;

		// Calculate the moments, used for standard statistics
		double m1 = moment(data, 0, 1, n);
		double m2 = moment(data, m1, 2, n - 1);
		double m3 = moment(data, m1, 3, n - 1);
		double m4 = moment(data, m1, 4, n - 1);
		f.set("mean", m1);
		f.set("stddev", Math.sqrt(m2));
		f.set("variance", m2);
		f.set("skew", m3 / m2 / Math.sqrt(m2));
		f.set("kurtosis", m4 / m2 / m2 - 3.0);

		Arrays.sort(data);
		double min = data[0];
		double max = data[n - 1];
		f.set("min", min);
		f.set("max", max);

		// Order statistics: using the Tukey hinge definition
		f.set("median", av(data, (n - 1) * 0.5));
		if (n % 2 == 0) {
			// Even data, include the median in upper and lower
			f.set("q1", av(data, (n / 2 - 1) * 0.5));
			f.set("q3", av(data, n / 2 + (n / 2 - 1) * 0.5));
		} else {
			// Odd data, do not include the median in upper and lower
			f.set("q1", av(data, (n - 1) * 0.25));
			f.set("q3", av(data, (n - 1) / 2 + (n - 1) * 0.25));
		}

		double minD = max - min;
		boolean allInteger = true;
		if (minD == 0) minD = Math.abs(max);
		for (int i = 1; i < data.length; i++) {
			double d = data[i] - data[i - 1];
			if (d > 0) minD = Math.min(minD, d);
			if (allInteger && data[i] != Math.round(data[i])) allInteger = false;
		}

		f.set("minDelta", minD);

		// minD is the minimum difference between items; now calculate the granularity by updating it so it
		// divides in evenly into all the differences
		double granularity = minD;
		for (int i = 1; i < data.length; i++) {
			double d = data[i] - data[i - 1];
			double extra = d % granularity;
			if (extra > 0) {
				if (granularity % extra == 0) granularity = extra;
				else {
					// Can't do it easily
					granularity = 0;
					break;
				}
			}
		}

		f.set("minDelta", minD);
		f.set("granularity", granularity);

		double range = max == min ? (max == 0 ? 1 : Math.abs(max)) : max - min;
		double places = Math.max(0, Math.round(4 - Math.log(range) / Math.log(10)));         // decimal places to show range
		f.set("decimalPlaces", allInteger && max - min > 5 ? 0 : places);
	}

	/*
	 * Calculates the centralized moment where
	 * c is the center,
	 * p is the power to raise to,
	 * N is the total weight (the amount to divide by)
	 */
	private static double moment(Double[] data, double c, int p, double N) {
		if (N <= 0) return Double.NaN;
		double sum = 0.0;
		for (Double element : data)
			sum += Math.pow(element - c, p);
		return sum / N;
	}

	private static double av(Double[] v, double index) {
		return (v[(int) Math.floor(index)] + v[(int) Math.ceil(index)]) / 2.0;
	}

	public static boolean creates(String key) {
		return "validNumeric".equals(key) || "mean".equals(key)
				|| "stddev".equals(key) || "variance".equals(key)
				|| "skew".equals(key) || "kurtosis".equals(key)
				|| "min".equals(key) || "max".equals(key)
				|| "q1".equals(key) || "q3".equals(key)
				|| "median".equals(key) || "granularity".equals(key)
				|| "decimalPlaces".equals(key);
	}
}
