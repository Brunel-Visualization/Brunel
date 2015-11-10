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
import org.brunel.data.io.CSV;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestSummary {

    // gender,bdate,educ,jobcat,salary,salbegin,jobtime,minority\n"
    // Male,19027,15,Manager,57000,27000,98,No\n"

    private final Dataset data = Dataset.make(CSV.read(CannedData.bank));

    @Test
    public void testGroups() {
        Dataset a = Summarize.transform(data, "gender=gender; count=:count");
        Assert.assertEquals("gender|count|#count|#row -- " +
                "Female|12|12|3, 4, 8, 9, 10, 11, 14, 20, 21, 23, 24, 25 -- " +
                "Male|13|13|1, 2, 5, 6, 7, 12, 13, 15, 16, 17, 18, \u2026", CannedData.dump(a));

        a = Summarize.transform(data, "gender=gender; educ=educ; count=:count");

        Assert.assertEquals("educ|gender|count|#count|#row -- "
                        + "8|Female|1|1|4 -- 8|Male|1|1|12 -- 12|Female|5|5|3, 8, 10, 20, 24 -- 12|Male|4|4|15, 16, 19, 22 -- "
                        + "15|Female|4|4|9, 14, 23, 25 -- 15|Male|6|6|1, 5, 6, 7, 13, 17 -- 16|Female|2|2|11, 21 -- 16|Male|2|2|2, 18",
                CannedData.dump(a));

    }

    @Test
    public void testPercentDifferentBases() {
        Dataset a = Summarize.transform(data, "gender=gender; jobcat=jobcat:base; #percent=#count:percent");
        Assert.assertEquals("gender|jobcat|#percent|#count|#row -- " +
                "Female|Clerical|52.2%|12|3, 4, 8, 9, 10, 11, 14, 20, 21, 23, 24, 25 -- " +
                "Male|Clerical|47.8%|11|2, 5, 6, 7, 12, 13, 15, 16, 17, 19, 22 -- " +
                "Male|Manager|100%|2|1, 18", CannedData.dump(a));
    }

    @Test
    public void testPercentSimple() {
        Dataset a = Summarize.transform(data, "gender=gender; #percent=#count:percent");
        Assert.assertEquals("gender|#percent|#count|#row -- " +
                "Female|48%|12|3, 4, 8, 9, 10, 11, 14, 20, 21, 23, 24, 25 -- " +
                "Male|52%|13|1, 2, 5, 6, 7, 12, 13, 15, 16, 17, 18, \u2026", CannedData.dump(a));
    }

    @Test
    public void testEmptyData() {
        Field x = Data.makeColumnField("x", null, new Object[]{1, 2});
        Field y = Data.makeConstantField("y", null, null, 2);
        Dataset a = Dataset.make(new Field[]{x, y});
        a = Summarize.transform(a, "x=x; y1=y:min; y2=y:sum; y3=y:iqr; y4=y:valid; y5=y:median");
        Assert.assertEquals("x|y1|y2|y3|y4|y5|#count|#row -- " +
                "1|?|?|?|0|?|1|1 -- 2|?|?|?|0|?|1|2", CannedData.dump(a));
    }

    /*
     * /*
     * Possible summaries are:
     * [numeric] mean, min, max, range, iqr, median, stddev
     * [any] count, valid, mode, unique
     */
    @Test
    public void testRangeStats() {
        Dataset a = Summarize.transform(data, "gender=gender; a=salary:range; b=salary:iqr");
        Assert.assertEquals(
                "gender|a|b|#count|#row -- " +
                        "Female|16,950\u202638,850|21,675\u202629,100|12|3, 4, 8, 9, 10, 11, 14, 20, 21, 23, 24, 25 -- " +
                        "Male|21,750\u2026103,750|28,350\u202645,000|13|1, 2, 5, 6, 7, 12, 13, 15, 16, 17, 18, \u2026",
                CannedData.dump(a));
    }

    @Test
    public void testSimpleCount() {
        Dataset a = Summarize.transform(data, "count = : count");
        Assert.assertEquals("count|#count|#row -- 25|25|1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, \u2026", CannedData.dump(a));
        assertEquals(true, a.fields[0].isNumeric());

        a = Summarize.transform(data, "COUNT = : count");
        Assert.assertEquals("COUNT|#count|#row -- 25|25|1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, \u2026", CannedData.dump(a));

        a = Summarize.transform(data, "g=gender:count");
        Assert.assertEquals("g|#count|#row -- 25|25|1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, \u2026", CannedData.dump(a));
    }

    /*
     * Possible summaries are:
     * [numeric] mean, min, max, range, iqr, median, stddev
     * [any] count, valid, mode, unique
     */
    @Test
    public void testSimpleStats() {
        String spec = "gender = gender; a = educ: mean; b = educ:min; c = educ:max; d = educ: valid; e = educ:median; f = educ:stddev; g = educ:unique ; h = educ:mode";
        Dataset a = Summarize.transform(data, spec);
        Assert.assertEquals(
                "gender|a|b|c|d|e|f|g|h|#count|#row -- " +
                        "Female|13.333333|8|16|12|13.5|2.3868326|4|12|12|3, 4, 8, 9, 10, 11, 14, 20, 21, 23, 24, 25 -- " +
                        "Male|13.692308|8|16|13|15|2.3232382|4|15|13|1, 2, 5, 6, 7, 12, 13, 15, 16, 17, 18, \u2026",
                CannedData.dump(a));
    }

    @Test
    public void testSimpleStatsNonNumeric() {
        String spec = "gender = gender; a = jobcat: mean; b = jobcat:min;  d = jobcat: valid; e = jobcat:median; f = jobcat:stddev; g = jobcat:unique ; h = jobcat:mode";
        Dataset a = Summarize.transform(data, spec);
        Assert.assertEquals("gender|a|b|d|e|f|g|h|#count|#row -- " +
                "Female|Clerical|?|12|?|?|1|Clerical|12|3, 4, 8, 9, 10, 11, 14, 20, 21, 23, 24, 25 -- " +
                "Male|Clerical|?|13|?|?|2|Clerical|13|1, 2, 5, 6, 7, 12, 13, 15, 16, 17, 18, \u2026", CannedData.dump(a));

    }

}
