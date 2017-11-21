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

import org.brunel.data.Field;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.DateUnit;

public class DateStats {

    public static void populate(Field f) {
        if (f.min() == null) return;        // Nope!

        // If we have a degenerate date range, choose units assuming the min is 0
        double days = f.max() - f.min();
        if (days == 0) days = f.max();
        DateUnit unit = getUnit(days);
        f.set("dateUnit", unit);
        Double minDelta = f.numProperty("minDelta");
        double factor = Math.min(1.0, Math.sqrt(f.valid()) / 7);          // With little data, decrease granularity
        f.set("dateFormat", getFormat(unit, minDelta * factor));
    }

    /* Return the binning unit based on the range of days spanned */
    public static DateUnit getUnit(double days) {
        for (DateUnit d : DateUnit.values()) {
            if (days > 3.5 * d.approxDaysPerUnit) return d;
            // Days are particularly nice -- prefer 3 ticks for days rather than a lot of hour ticks
            if (d == DateUnit.day && days >= 2.5 * d.approxDaysPerUnit) return d;
        }
        return DateUnit.second;
    }

    public static DateFormat getFormat(DateUnit unit, double granularity) {
        // HourMinSec, HourMin, DayHour, YearMonthDay, YearMonth, Year;
        if (granularity > 360) return DateFormat.Year;
        if (granularity > 13) return DateFormat.YearMonth;
        if (granularity > 0.9) return DateFormat.YearMonthDay;

        // We want to show time, but do we also need the days?
        if (unit.ordinal() < DateUnit.hour.ordinal()) return DateFormat.DayHour;
        if (granularity > 0.9 / 24 / 60) return DateFormat.HourMin;
        return DateFormat.HourMinSec;
    }

    public static boolean creates(String key) {
        return "dateUnit".equals(key) || "dateFormat".equals(key);
    }

}
