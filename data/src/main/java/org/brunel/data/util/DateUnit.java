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

import org.brunel.translator.JSTranslation;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public enum DateUnit {

    century(365 * 100, 10), decade(365 * 10, 10), year(365, 10),
    quarter(365.0 / 4, 4), month(30, 4), week(7, 4), day(1, 7),
    hour(1 / 24.0, 24), minute(1 / 24.0 / 60.0, 60), second(1 / 24.0 / 3600.0, 60);

    /* Return the largest date <= d that is a multiple of the given number of units */
    @JSTranslation(js = {
            "if (u == $$CLASS$$.day) {",
            "	return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), $$CLASS$$.floorNumeric(d.getUTCDate(), multiple, 1)));",
            "} else if (u == $$CLASS$$.week) {",
            "	return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), $$CLASS$$.floorNumeric(d.getUTCDate(), multiple*7, 1)));",
            "} else if (u == $$CLASS$$.month) {",
            "	return new Date(Date.UTC(d.getUTCFullYear(), $$CLASS$$.floorNumeric(d.getUTCMonth(), multiple, 0)));",
            "} else if (u == $$CLASS$$.quarter) {",
            "	return new Date(Date.UTC(d.getUTCFullYear(), $$CLASS$$.floorNumeric(d.getUTCMonth(), multiple*3, 0)));",
            "} else if (u == $$CLASS$$.year) {",
            "	return new Date(Date.UTC($$CLASS$$.floorNumeric(d.getUTCFullYear(), multiple, 0), 0));",
            "} else if (u == $$CLASS$$.decade) {",
            "	return new Date(Date.UTC($$CLASS$$.floorNumeric(d.getUTCFullYear(), multiple*10, 0), 0));",
            "} else if (u == $$CLASS$$.century) {",
            "	return new Date(Date.UTC($$CLASS$$.floorNumeric(d.getUTCFullYear(), multiple*100, 0), 0));",
            "}",
            "var c = new Date(d.getTime());",
            "if (u == $$CLASS$$.second) {",
            "	c.setUTCSeconds($$CLASS$$.floorNumeric(d.getUTCSeconds(), multiple, 0));",
            "} else if (u == $$CLASS$$.minute) {",
            "	c.setUTCSeconds(0);",
            "	c.setUTCMinutes($$CLASS$$.floorNumeric(d.getUTCMinutes(), multiple, 0));",
            "} else if (u == $$CLASS$$.hour) {",
            "	c.setUTCSeconds(0); c.setUTCMinutes(0);",
            "	c.setUTCHours($$CLASS$$.floorNumeric(d.getUTCHours(), multiple, 0));",
            "} else if (u == $$CLASS$$.hour) {",
            "	c.setUTCSeconds(0); c.setUTCMinutes(0);",
            "	c.setUTCHours($$CLASS$$.floorNumeric(d.getUTCHours(), multiple, 0));",
            "} else",
            "	throw 'Invalid date unit ' + u;",
            "return c;"
    })
    public static Date floor(Date d, DateUnit u, int multiple) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTime(d);
        // Dates are ordered highest (century) to lowest (second)
        // Set all the lower fields to zero
        if (u.ordinal() < DateUnit.second.ordinal())
            c.set(Calendar.SECOND, 0);
        if (u.ordinal() < DateUnit.minute.ordinal())
            c.set(Calendar.MINUTE, 0);
        if (u.ordinal() < DateUnit.hour.ordinal())
            c.set(Calendar.HOUR_OF_DAY, 0);
        if (u.ordinal() < DateUnit.week.ordinal())
            c.set(Calendar.DAY_OF_MONTH, 1);
        if (u.ordinal() < DateUnit.quarter.ordinal())
            c.set(Calendar.MONTH, 0);
        // Handle the highest field
        if (u == DateUnit.second)
            setCalendarField(c, Calendar.SECOND, multiple, 0);
        else if (u == DateUnit.minute)
            setCalendarField(c, Calendar.MINUTE, multiple, 0);
        else if (u == DateUnit.hour)
            setCalendarField(c, Calendar.HOUR_OF_DAY, multiple, 0);
        else if (u == DateUnit.day)
            setCalendarField(c, Calendar.DAY_OF_MONTH, multiple, 1);
        else if (u == DateUnit.week)
            setCalendarField(c, Calendar.DAY_OF_MONTH, multiple * 7, 1);
        else if (u == DateUnit.month)
            setCalendarField(c, Calendar.MONTH, multiple, 0);
        else if (u == DateUnit.quarter)
            setCalendarField(c, Calendar.MONTH, multiple * 3, 0);
        else if (u == DateUnit.year)
            setCalendarField(c, Calendar.YEAR, multiple, 0);
        else if (u == DateUnit.decade)
            setCalendarField(c, Calendar.YEAR, multiple * 10, 0);
        else if (u == DateUnit.century)
            setCalendarField(c, Calendar.YEAR, multiple * 100, 0);
        else
            throw new IllegalStateException("Strange date unit: " + u);

        return c.getTime();
    }

    @JSTranslation(ignore = true)
    private static void setCalendarField(Calendar c, int field, int multiple, int min) {
        int v = c.get(field);
        c.set(field, (int) floorNumeric(v, multiple, min));
    }

    private static double floorNumeric(int value, int multiple, int offset) {
        return multiple * Math.floor((value - offset) / multiple) + offset;
    }

    @JSTranslation(js = {
            "var c = new Date(d.getTime());",
            "if (u == $$CLASS$$.second) c.setUTCSeconds(c.getUTCSeconds()+delta);",
            "else if (u == $$CLASS$$.minute) c.setUTCMinutes(c.getUTCMinutes()+delta);",
            "else if (u == $$CLASS$$.hour) c.setUTCHours(c.getUTCHours()+delta);",
            "else if (u == $$CLASS$$.day) c.setUTCDate(c.getUTCDate()+delta);",
            "else if (u == $$CLASS$$.week) c.setUTCDate(c.getUTCDate()+7*delta);",
            "else if (u == $$CLASS$$.month) c.setUTCMonth(c.getUTCMonth()+delta);",
            "else if (u == $$CLASS$$.quarter) c.setUTCMonth(c.getUTCMonth()+3*delta);",
            "else if (u == $$CLASS$$.year) c.setUTCFullYear(c.getUTCFullYear()+delta);",
            "else if (u == $$CLASS$$.decade) c.setUTCFullYear(c.getUTCFullYear()+10*delta);",
            "else if (u == $$CLASS$$.century) c.setUTCFullYear(c.getUTCFullYear()+100*delta);",
            "return c; "
    })
    public static Date increment(Date d, DateUnit u, int delta) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTime(d);
        if (u == DateUnit.second)
            c.add(Calendar.SECOND, delta);
        else if (u == DateUnit.minute)
            c.add(Calendar.MINUTE, delta);
        else if (u == DateUnit.hour)
            c.add(Calendar.HOUR_OF_DAY, delta);
        else if (u == DateUnit.day)
            c.add(Calendar.DAY_OF_MONTH, delta);
        else if (u == DateUnit.week)
            c.add(Calendar.DAY_OF_MONTH, delta * 7);
        else if (u == DateUnit.month)
            c.add(Calendar.MONTH, delta);
        else if (u == DateUnit.quarter)
            c.add(Calendar.MONTH, delta * 3);
        else if (u == DateUnit.year)
            c.add(Calendar.YEAR, delta);
        else if (u == DateUnit.decade)
            c.add(Calendar.YEAR, delta * 10);
        else if (u == DateUnit.century)
            c.add(Calendar.YEAR, delta * 100);
        else
            throw new IllegalStateException("Strange date unit: " + u);
        return c.getTime();
    }

    public final double approxDaysPerUnit;            // About this many days in this unit
    public final int base;                            // What base to use for these

    DateUnit(double days, int base) {
        approxDaysPerUnit = days;
        this.base = base;
    }

}
