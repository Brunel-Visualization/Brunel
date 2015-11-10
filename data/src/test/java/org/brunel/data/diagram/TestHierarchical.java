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

package org.brunel.data.diagram;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.modify.Filter;
import org.brunel.data.io.CSV;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestHierarchical {

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
    public void testPreservesKeys() {
        Dataset trimmed = Filter.transform(simple, "A !is a");
        Node data = Hierarchical.makeByNestingFields(trimmed, null, "A").root;
        Assert.assertEquals("((0-1) (1-1 2-1 3-1))", dumpTree(data));

        assertEquals("", data.key);                 // top level has unknown key

        Node[] children = (Node[]) data.children;           // the top level children (b and c groups)
        assertEquals(2, children.length);
        assertEquals("-b", children[0].key);
        assertEquals("-c", children[1].key);

        Node[] group_b = (Node[]) children[0].children;     // 'b' group
        assertEquals(1, group_b.length);
        assertEquals(2, group_b[0].key);                  // 'b' item has index 2 in the original data

        Node[] group_c = (Node[]) children[1].children;     // 'c' group
        assertEquals(3, group_c.length);
        assertEquals(3, group_c[0].key);
        assertEquals(4, group_c[1].key);
        assertEquals(5, group_c[2].key);
    }


    @Test
    public void testOneLevel() {
        Node data = Hierarchical.makeByNestingFields(simple, "D", "A").root;
        Assert.assertEquals("((0-4) (1-3) (2-2 3-1 4-1))", dumpTree(data));

        data = Hierarchical.makeByNestingFields(simple, "D", "B").root;
        Assert.assertEquals("((0-4 1-3 3-1) (2-2 4-1))", dumpTree(data));
    }

    @Test
    public void testTwoLevels() {
        Node data = Hierarchical.makeByNestingFields(simple, "D", "A", "B").root;
        Assert.assertEquals("(((0-4)) ((1-3)) ((2-2 4-1) (3-1)))", dumpTree(data));

        Node level1Inner = ((Node[]) data.children)[0];
        Node level2Inner = ((Node[]) level1Inner.children)[0];
        Node leaf = ((Node[]) level2Inner.children)[0];

        assertEquals("", data.key);
        assertEquals("-a", level1Inner.key);
        assertEquals("-a-x", level2Inner.key);
        assertEquals(1, leaf.key);
    }

    @Test
    public void testZeroLevel() {
        Node data = Hierarchical.makeByNestingFields(simple, "D").root;
        Assert.assertEquals("(0-4 1-3 2-2 3-1 4-1)", dumpTree(data));
    }

    // recursive output of the tree
    private String dumpTree(Node node) {
        String s = "";
        if (node.row != null || node.value > 0) s += node.row + "-" + Data.format(node.value, false);
        Node[] children = (Node[]) node.children;
        if (children != null) {
            s += "(";
            for (Node n : children) {
                if (n != children[0]) s += " ";
                s += dumpTree(n);
            }
            s += ")";
        }
        return s;
    }

}
