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
import org.brunel.data.io.CSV;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestFilter {

    private static final String csv = Data.join(new String[]{
            "A,B,C,D",
            "a,x,1,4",
            "b,x,2,3",
            "c,y,1,2",
            "c,,2,1",
    }, "\n");

    private static final Dataset simple = Dataset.make(CSV.read(csv));

    @Test
    public void testFilterByIn() {
        // No change because filters all ok
        Dataset a = simple.filter("A in a,z");
        assertEquals(a, simple);

        a = simple.filter("A in c,z");
        assertEquals("A|B|C|D|#count|#row -- c|y|1|2|1|3 -- c|?|2|1|1|4", CannedData.dumpData(a));
        assertEquals("c", Data.join(a.fields[0].categories()));

        a = simple.filter("D in 1.5, 3.5");
        assertEquals("A|B|C|D|#count|#row -- b|x|2|3|1|2 -- c|y|1|2|1|3", CannedData.dumpData(a));
    }

    @Test
    public void testFilterByRanks() {
        // No change because filters all ok
        Dataset a = simple.filter("A ranked 1,4");
        assertEquals(a, simple);

        a = simple.filter("A ranked -1,1000");
        assertEquals(a, simple);

        a = simple.filter("C ranked 1,1");
        assertEquals("A|B|C|D|#count|#row -- b|x|2|3|1|2 -- c|?|2|1|1|4", CannedData.dumpData(a));

        a = simple.filter("B !ranked 2,100");
        assertEquals("A|B|C|D|#count|#row -- c|y|1|2|1|3", CannedData.dumpData(a));

    }

    @Test
    public void testFilterByIs() {
        // No change because no filters
        Dataset a = simple.filter("");
        assertEquals(a, simple);

        // No change because filters include all of them
        a = simple.filter("A is a,c,b");
        assertEquals(a, simple);

        a = simple.filter("A is c");
        assertEquals("A|B|C|D|#count|#row -- c|y|1|2|1|3 -- c|?|2|1|1|4", CannedData.dumpData(a));

        a = simple.filter("D is 1,3.0");
        assertEquals("A|B|C|D|#count|#row -- b|x|2|3|1|2 -- c|?|2|1|1|4", CannedData.dumpData(a));
    }

    @Test
    public void testFilterByNot() {
        // No change because filters all ok
        Dataset a = simple.filter("A !is d");
        assertEquals(a, simple);

        a = simple.filter("A !is c");
        assertEquals("A|B|C|D|#count|#row -- a|x|1|4|1|1 -- b|x|2|3|1|2", CannedData.dumpData(a));

        a = simple.filter("D !is 1,3.0");
        assertEquals("A|B|C|D|#count|#row -- a|x|1|4|1|1 -- c|y|1|2|1|3", CannedData.dumpData(a));
    }

    @Test
    public void testFilterByValid() {
        // No change because filters all ok
        Dataset a = simple.filter("A valid");
        assertEquals(a, simple);

        a = simple.filter("B valid");
        assertEquals("A|B|C|D|#count|#row -- a|x|1|4|1|1 -- b|x|2|3|1|2 -- c|y|1|2|1|3", CannedData.dumpData(a));

    }

    @Test
    public void testFilterCombinations() {
        // No change because filters all ok
        Dataset a = simple.filter("A in a,z; C valid");
        assertEquals(a, simple);

        a = simple.filter("B valid; D in -1,3");
        assertEquals("A|B|C|D|#count|#row -- b|x|2|3|1|2 -- c|y|1|2|1|3", CannedData.dumpData(a));
    }


    @Test
    public void testFilterWithMissing() {
        Dataset a = simple.filter("B is y");
        assertEquals("A|B|C|D|#count|#row -- c|y|1|2|1|3", CannedData.dumpData(a));

        a = simple.filter("B is y || missing");
        assertEquals("A|B|C|D|#count|#row -- c|y|1|2|1|3 -- c|?|2|1|1|4", CannedData.dumpData(a));
    }

}
