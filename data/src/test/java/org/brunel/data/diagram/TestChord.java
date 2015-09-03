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

package org.brunel.data.diagram;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestChord {

    private static final String csv = Data.join(new String[]{
            "A,B,C,D",
            "a,x,1,4",
            "b,x,2,3",
            "c,y,1,2",
            "c,x,2,1",
            "c,y,5,1",
    }, "\n");

    private static final Dataset simple = Dataset.make(CSV.read(csv));

    @Test
    public void testGroupNames() {
        Chord data = new Chord(simple, "A", "B", null);
        assertEquals("a", data.group(0));
        assertEquals("b", data.group(1));
        assertEquals("c", data.group(2));
        assertEquals("x", data.group(3));
        assertEquals("y", data.group(4));

    }

    @Test
    public void testIndices() {
        Chord data = new Chord(simple, "A", "B", null);
        assertEquals(1, data.index(1, 3));
        assertEquals(3, data.index(2, 3));
        assertEquals(4, data.index(2, 4));
        assertEquals(1, data.index(3, 1));
        assertEquals(3, data.index(3, 2));
        assertEquals(4, data.index(4, 2));
    }

    @Test
    public void testMatrix() {
        double[][] data = new Chord(simple, "A", "B", null).matrix();
        Assert.assertEquals(5, data.length);
        // Columns:   a  b  c  x  y
        assertEquals("0, 0, 0, 1, 0", Data.join(data[0]));      // a -> x
        assertEquals("0, 0, 0, 1, 0", Data.join(data[1]));      // b -> x
        assertEquals("0, 0, 0, 1, 2", Data.join(data[2]));      // c -> x, y
        assertEquals("1, 1, 1, 0, 0", Data.join(data[3]));      // x ->
        assertEquals("0, 0, 2, 0, 0", Data.join(data[4]));      // y ->

        data = new Chord(simple, "A", "B", "C").matrix();
        Assert.assertEquals(5, data.length);
        // Columns:   a  b  c  x  y
        assertEquals("0, 0, 0, 1, 0", Data.join(data[0]));      // a -> x
        assertEquals("0, 0, 0, 2, 0", Data.join(data[1]));      // b -> x
        assertEquals("0, 0, 0, 2, 6", Data.join(data[2]));      // c -> x, y
        assertEquals("1, 2, 2, 0, 0", Data.join(data[3]));      // x ->
        assertEquals("0, 0, 6, 0, 0", Data.join(data[4]));      // y ->
    }

}
