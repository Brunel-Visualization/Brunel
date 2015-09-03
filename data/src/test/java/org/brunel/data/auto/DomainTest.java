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

package org.brunel.data.auto;

import org.brunel.data.Data;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DomainTest {

    @Test
    public void testNominalCombinable() {
        Domain a = new Domain().add(Data.makeColumnField("a", null, new Integer[]{1, 2, 9, 10}));
        Domain b = new Domain().add(Data.makeColumnField("b", null, new Integer[]{1, 2}));
        Domain c = new Domain().add(Data.makeColumnField("c", null, new Integer[]{3, 4, 5, 6, 9, 10}));

        assertEquals(1.0, a.mergedUnwastedSpace(a), 1e-6);
        assertEquals(1.0, b.mergedUnwastedSpace(b), 1e-6);
        assertEquals(1.0, c.mergedUnwastedSpace(c), 1e-6);

        assertEquals(1.0, a.mergedUnwastedSpace(b), 1e-6);
        assertEquals(1.0, b.mergedUnwastedSpace(a), 1e-6);

        assertEquals(0.75, a.mergedUnwastedSpace(c), 1e-6);
        assertEquals(0.75, c.mergedUnwastedSpace(a), 1e-6);

        assertEquals(0.75, b.mergedUnwastedSpace(c), 1e-6);
        assertEquals(0.75, c.mergedUnwastedSpace(b), 1e-6);
    }

    @Test
    public void testNumericCombinable() {
        Domain a = new Domain().add(Data.makeIndexingField("a", null, 5));
        Domain b = new Domain().add(Data.makeIndexingField("b", null, 10));
        Domain c = new Domain().add(Data.toNumeric(Data.makeColumnField("c", null, new Integer[]{2, 5, -5})));
        Domain d = new Domain().add(Data.toNumeric(Data.makeColumnField("d", null, new Integer[]{20, 21})));

        assertEquals(1.0, a.mergedUnwastedSpace(a), 1e-6);
        assertEquals(1.0, b.mergedUnwastedSpace(b), 1e-6);
        assertEquals(1.0, c.mergedUnwastedSpace(c), 1e-6);

        assertEquals(1.0, a.mergedUnwastedSpace(b), 1e-6);
        assertEquals(1.0, b.mergedUnwastedSpace(a), 1e-6);

        assertEquals(1.0, a.mergedUnwastedSpace(c), 1e-6);
        assertEquals(1.0, c.mergedUnwastedSpace(a), 1e-6);

        assertEquals(0.6666666666666666, b.mergedUnwastedSpace(c), 1e-6);
        assertEquals(0.6666666666666666, c.mergedUnwastedSpace(b), 1e-6);

        assertEquals(0.2, d.mergedUnwastedSpace(a), 1e-6);
        assertEquals(0.45, d.mergedUnwastedSpace(b), 1e-6);
    }
}
