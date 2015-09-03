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

import org.brunel.translator.JSTranslation;

import java.util.Date;

public enum DateFormat {
    HourMinSec, HourMin, DayHour, YearMonthDay, YearMonth, Year;

    /*
     * Format using the desired method.
     * In Javascript, UTCString is 'Tue, 10 Mar 2015 14:59:52 GMT',
     * so splitting into parts gives 0:weekday, 1:day_of_month, 2:month, 3:year, 4:HMS
     */
    @JSTranslation(js = {
            "var p = date.toUTCString().split(' ');",
            "var t = this.toString();",
            "if (t == 'HourMinSec') return p[4];",
            "if (t == 'Year') return p[3];",
            "if (t == 'YearMonth') return p[2] + ' ' + Number(p[3]);",
            "if (t == 'YearMonthDay') return p[2] + ' ' + Number(p[1]) + ', ' + Number(p[3]);",
            "var q = p[4].split(':');",
            "var hm = q[0] + ':' + q[1];",
            "if (t == 'HourMin') return hm;",
            "return p[2] + ' ' + Number(p[1]) + ' ' +  hm;"
    })
    public String format(Date date) {
        return Dates.format(date, this);
    }
}
