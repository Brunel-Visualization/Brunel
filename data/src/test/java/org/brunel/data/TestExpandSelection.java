/*
 * Copyright (c) 2016 IBM Corporation and others.
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

import org.brunel.data.io.CSV;
import org.brunel.data.modify.Summarize;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class TestExpandSelection {

    // gender,bdate,educ,jobcat,salary,salbegin,jobtime,minority\n"
    // Male,19027,15,Manager,57000,27000,98,No\n"
    private final Dataset data = Dataset.make(CSV.read(CannedData.bank));

    @Test
    public void testOnSummary() {

        Dataset a = Summarize.transform(data, "gender=gender; count=:count");


        // Validate the summary was what we expected (12 female, 13 male)
        assertEquals(2, a.rowCount());
        Field summaryGender = a.field("gender");
        Field summaryCount = a.field("#count");
        assertEquals("Female", summaryGender.value(0));
        assertEquals("Male", summaryGender.value(1));
        assertEquals(12.0, summaryCount.value(0));
        assertEquals(13.0, summaryCount.value(1));

        // The selection  fields
        Field select = data.field("#selection");
        Field summarySelect = a.field("#selection");

        String[] keys = {"gender"};

        // Select row 0
        data.modifySelection("sel", 0, a, keys);
        assertEquals("N,N,Y,Y,N,N,N,Y,Y,Y,Y,N,N,Y,N,N,N,N,N,Y,Y,N,Y,Y,Y", CannedData.dump(select));

        // Select row 1
        data.modifySelection("sel", 1, a, keys);
        assertEquals("Y,Y,N,N,Y,Y,Y,N,N,N,N,Y,Y,N,Y,Y,Y,Y,Y,N,N,Y,N,N,N", CannedData.dump(select));

    }


}
