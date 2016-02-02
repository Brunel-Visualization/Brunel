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

package org.brunel.data.modify;

import org.brunel.data.CannedData;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.junit.Assert;
import org.junit.Test;

public class TestFit {

    private static final String csv = Data.join(new String[]{
            "C,X,Y",
            "a,1,1",
            "a,2,2",
            "b,1,3",
            "b,2,3",
            "b,3,3"
    }, "\n");

    private static final Dataset simple = Dataset.make(CSV.read(csv));



    @Test
    public void testSimple() {
        // Fit the whole thing --  the line Y = 1.5 + 0.5x
        Dataset a = simple.summarize("Y=Y:fit; X=X:base");
        Assert.assertEquals("X|Y|#count|#row -- 1|2|2|1, 3 -- 2|2.5|2|2, 4 -- 3|3|1|5", CannedData.dump(a));
    }

    @Test
    public void testGrouped() {
        // Fit two groups -- two lines
        // first is y=x, second is y=3

        Dataset a = simple.summarize("Y=Y:fit; X=X:base; C=C");
        Assert.assertEquals("C|X|Y|#count|#row -- a|1|1|1|1 -- a|2|2|1|2 -- b|1|3|1|3 -- b|2|3|1|4 -- b|3|3|1|5", CannedData.dump(a));
    }


}
