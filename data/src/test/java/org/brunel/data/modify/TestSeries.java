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

package org.brunel.data.modify;

import org.brunel.data.CannedData;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.junit.Assert;
import org.junit.Test;

public class TestSeries {

    private static final String csv = Data.join(new String[]{
            "A,B,C,D",
            "a,x,1,4",
            "b,x,2,3",
            "c,y,1,2",
            "c,x,2,1",
    }, "\n");

    private static final Dataset simple = Dataset.make(CSV.read(csv));

    @Test
    public void testSimple() {
        // One series leaves it unchanged
        Dataset a = simple.series("A; B");
        Assert.assertEquals("A|B|C|D|#count|#row -- a|x|1|4|1|1 -- b|x|2|3|1|2 -- c|y|1|2|1|3 -- c|x|2|1|1|4", CannedData.dump(a));


        // two series is where the action is
        a = simple.series("C,D; A,B");
        Assert.assertEquals("#series|#values|A|B|#row|#count -- C|1|a|x|1|1 -- C|2|b|x|2|1 -- C|1|c|y|3|1 -- C|2|c|x|4|1 -- D|4|a|x|1|1 -- D|3|b|x|2|1 -- D|2|c|y|3|1 -- D|1|c|x|4|1", CannedData.dump(a));

        // make sure duplicated fields work
        a = simple.series("C,D; D");
        Assert.assertEquals("#series|#values|D|#row|#count -- C|1|4|1|1 -- C|2|3|2|1 -- C|1|2|3|1 -- C|2|1|4|1 -- D|4|4|1|1 -- D|3|3|2|1 -- D|2|2|3|1 -- D|1|1|4|1", CannedData.dump(a));



    }

}
