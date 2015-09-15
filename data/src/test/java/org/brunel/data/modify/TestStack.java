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
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestStack {

    private static final String csv = Data.join(new String[]{
            "A,B,C,D",
            "a,x,1,4",
            "b,x,2,3",
            "c,y,1,2",
            "c,x,2,1",
    }, "\n");

    private static final Dataset simple = Dataset.make(CSV.read(csv));

    @Test
    public void testFullStackingA() {

        Dataset a = Stack.transform(simple, "D; A; B; true");

        /*
         This should result in the following data:

            A   B   C   D   low up
            a   x   1   4   0   4
            a   y   -   -   4   4
            b   x   2   3   0   3
            b   y   -   -   3   3
            c   x   2   1   0   1
            c   y   1   2   1   3
        */

        assertEquals(6, a.rowCount());
        assertEquals("A|B|C|D|D$lower|D$upper|#count|#row -- a|x|1|4|0|4|1|1 -- a|y|?|?|4|4|?|? -- b|x|2|3|0|3|1|2 -- b|y|?|?|3|3|?|? -- c|x|2|1|0|1|1|4 -- c|y|1|2|1|3|1|3",
                CannedData.dump(a));

    }


    @Test
    public void testStackingNoX() {
        Dataset a = Stack.transform(simple,"D; ; ; false");
        /*
         This should result in the following data:
            A   B   C   D   low up
            a   x   1   4   0   4
            b   x   2   3   0   3
            c   x   2   1   0   1
            c   y   1   2   1   3
        */
        assertEquals("A|B|C|D|D$lower|D$upper|#count|#row -- c|x|2|1|0|1|1|4 -- c|y|1|2|1|3|1|3 -- b|x|2|3|3|6|1|2 -- a|x|1|4|6|10|1|1",
                CannedData.dump(a));
    }

    @Test
    public void testStackingOrder() {
        Dataset ordered = Dataset.make(CSV.read(csv));
        ordered.field("B").set("categories", new Object[]{"y", "x"});            // reverse order
        Dataset a = Stack.transform(ordered,"D; ; B; false");
        /*
         This should result in the following data:
            A   B   C   D   low up
            a   x   1   4   0   4
            b   x   2   3   0   3
            c   x   2   1   0   1
            c   y   1   2   1   3
        */
        assertEquals("A|B|C|D|D$lower|D$upper|#count|#row -- c|y|1|2|0|2|1|3 -- c|x|2|1|2|3|1|4 -- b|x|2|3|3|6|1|2 -- a|x|1|4|6|10|1|1",
                CannedData.dump(a));
    }



    @Test
    public void testFullStackingB() {

        Dataset a = Stack.transform(simple, "D; A; B,C; true");

        /*
         This should result in the following data:

            A   B   C   D   low up
            a   x   1   4   0   4
            a   x   2   -   4   4
            a   y   1   -   4   4
            a   y   2   -   4   4
            b   x   1   -   0   0
            b   x   2   3   0   3
            b   y   1   -   3   3
            b   y   2   -   3   3
            c   x   1   -   0   0
            c   x   2   1   0   1
            c   y   1   2   1   3
            c   y   2   -   3   3
        */

        assertEquals(12, a.rowCount());

    }

    @Test
    public void testSimpleStacking() {

        Dataset a = Stack.transform(simple,"D; A; B; false");

        /*
         This should result in the following data:

            A   B   C   D   low up
            a   x   1   4   0   4
            b   x   2   3   0   3
            c   x   2   1   0   1
            c   y   1   2   1   3
        */

        assertEquals("A|B|C|D|D$lower|D$upper|#count|#row -- a|x|1|4|0|4|1|1 -- b|x|2|3|0|3|1|2 -- c|x|2|1|0|1|1|4 -- c|y|1|2|1|3|1|3",
                CannedData.dump(a));
    }

    @Test public void testLargeData() {
        // Ensure that stacking is not slow. This test was due to a defect we found with large data.
        // The test will always work, but should not kill the system doing so ...
        int N = 5000;

        // Make some vaguely random numbers for our "bar chart" : bar x(a) y(c) color(b) stack
        String[] a = new String[N];
        String[] b = new String[N];
        Double[] c = new Double[N];
        int s = 3;
        for (int i=0; i< N; i++) {
            s = (s*s) % (11*19);
            a[i] = "A" + (i%5 + 1);
            b[i] = "B" + (s%4 + 1);
            c[i] = (double) Math.round(s / (i%4+1));
        }

        Field fa = Data.makeColumnField("a", null, a);
        Field fb = Data.makeColumnField("b", null, b);
        Field fc = Data.makeColumnField("c", null, c);

        Dataset data = Dataset.make(new Field[] {fa, fb, fc});
        long t1 = System.currentTimeMillis();
        data.stack("a; c; b; false");
        long t2 = System.currentTimeMillis();
        if(t2 - t1 > 1000) throw new IllegalStateException("Stack ran in " + (t2-t1) + "ms on " + N + " items");
    }

}
