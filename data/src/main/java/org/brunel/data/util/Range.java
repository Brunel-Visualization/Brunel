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

package org.brunel.data.util;

import org.brunel.data.Data;
import org.brunel.translator.JSTranslation;

public class Range implements Comparable<Range> {

    @JSTranslation(ignore = true)
    public static Range make(double low, double high) {
        return make(low, high, null);
    }

    public static Range make(double low, double high, DateFormat df) {
        String lowRep = f(low, df);
        String highRep = f(high, df);
        String name;
        if (Double.isInfinite(low))
            name = Double.isInfinite(high) ? "\u2210" : "< " + highRep;
        else name = Double.isInfinite(high) ? "\u2265 " + lowRep : lowRep + "\u2026" + highRep;

        return new Range(low, high, name);
    }

    private static String f(double v, DateFormat df) {
        return df == null ? Data.formatNumeric(v, true) : df.format(Data.asDate(v));
    }

    public final double high;
    public final double low;
    private final String name;

    public Range(double low, double high, String name) {
        this.low = low;
        this.high = high;
        this.name = name;
    }

    public int compareTo(Range o) {
        return Data.compare(asNumeric(), o.asNumeric());
    }

     public Double asNumeric() {
        if (Double.isInfinite(low)) return Double.isInfinite(high) ? null : high;
        return Double.isInfinite(high) ? low : (low + high) / 2;
    }

    @JSTranslation(js = {"return this.name;"})
    public int hashCode() {
        long temp = Double.doubleToLongBits(high);
        int result = 31 + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(low);
        return 31 * result + (int) (temp ^ (temp >>> 32));
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof Range && ((Range) obj).low == low && ((Range) obj).high == high;
    }

    public String toString() {
        return name;
    }

}
