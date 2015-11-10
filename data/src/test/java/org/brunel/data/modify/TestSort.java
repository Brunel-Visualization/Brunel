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
import org.brunel.data.summary.FieldRowComparison;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class TestSort {

    private static final String csv = Data.join(new String[]{
            "A,B,C,D",
            "a,x,1,4",
            "b,x,2,3",
            "c,y,1,2",
            "c,x,2,1",
    }, "\n");

    private static final Dataset simple = Dataset.make(CSV.read(csv));

    @Test
    public void testSortNoChangeInRowOrder() {
        Dataset a = simple.sort("");
        assertEquals(CannedData.dump(simple), CannedData.dump(a));

        a = simple.sort("A");
        assertEquals(CannedData.dump(simple), CannedData.dump(a));

        a = simple.sort("D");
        assertEquals(CannedData.dump(simple), CannedData.dump(a));

    }


    @Test
    public void testSortRanking() {
        FieldRowComparison comparisonWithRows = new FieldRowComparison(new Field[] { simple.field("B")}, null, true);

        // The order should be 0,1,3,2
        int[] order = comparisonWithRows.makeSortedOrder(4);
        assertEquals(0, order[0]);
        assertEquals(1, order[1]);
        assertEquals(3, order[2]);
        assertEquals(2, order[3]);

        FieldRowComparison comparison = new FieldRowComparison(new Field[] { simple.field("B")}, null, false);
        double[] ranking = Sort.makeRowRanking(order, comparison);
        assertEquals(2, ranking[0], 0.01);
        assertEquals(2, ranking[1], 0.01);
        assertEquals(4, ranking[2], 0.01);
        assertEquals(2, ranking[3], 0.01);

        Object[] data = Sort.categoriesFromRanks(simple.field("A"), ranking);
        assertEquals(3, data.length);
        assertEquals("a", data[0]);
        assertEquals("b", data[1]);
        assertEquals("c", data[2]);
    }

    @Test
    public void testSortRowOrder() {
        Dataset a = simple.sort("B:ascending");
        assertEquals("A|B|C|D|#count|#row -- a|x|1|4|1|1 -- b|x|2|3|1|2 -- c|x|2|1|1|4 -- c|y|1|2|1|3", CannedData.dump(a));
        assertEquals("a, b, c", Data.join(a.fields[0].categories()));       // c is biggest because it has ranks 1+2,
        assertEquals("x, y", Data.join(a.fields[1].categories()));
        assertEquals("1, 2", Data.join(a.fields[2].categories()));
        assertEquals("1, 2, 3, 4", Data.join(a.fields[3].categories()));

        a = simple.sort("C:ascending; D:ascending");
        assertEquals("A|B|C|D|#count|#row -- c|y|1|2|1|3 -- a|x|1|4|1|1 -- c|x|2|1|1|4 -- b|x|2|3|1|2", CannedData.dump(a));
        assertEquals("a, b, c", Data.join(a.fields[0].categories()));       // c is biggest because it has ranks 1+2,

        a = simple.sort("C:descending; D:descending");
        assertEquals("A|B|C|D|#count|#row -- b|x|2|3|1|2 -- c|x|2|1|1|4 -- a|x|1|4|1|1 -- c|y|1|2|1|3", CannedData.dump(a));
    }

    @Test
    public void testSortSpeed() {
        // Ensure that stacking is not slow. This test was due to a defect we found with large data.
        // The test will always work, but should not kill the system doing so ...
        int N = 2000;

        // Make some vaguely random numbers for our "bar chart" : bar x(a) y(c) color(b) stack
        String[] a = new String[N];
        String[] b = new String[N];
        Double[] c = new Double[N];
        int s = 3;
        for (int i = 0; i < N; i++) {
            s = (s * s) % (11 * 19);
            a[i] = "A" + (i % 5 + 1);
            b[i] = "B" + (s % 4 + 1);
            c[i] = (double) Math.round(s / (i % 4 + 1));
        }

        Field fa = Data.makeColumnField("a", null, a);
        Field fb = Data.makeColumnField("b", null, b);
        Field fc = Data.makeColumnField("c", null, c);

        Dataset data = Dataset.make(new Field[]{fa, fb, fc});
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            data.sort("a");
            data.sort("b");
            data.sort("c");
        }
        long t2 = System.currentTimeMillis();
        if (t2 - t1 > 2000) throw new IllegalStateException("Sort ran in " + Math.round((t2 - t1)/5) + "ms on " + N + " items");
    }

}
