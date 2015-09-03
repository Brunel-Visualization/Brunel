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

package org.brunel.action;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActionTest {

    @Test
    public void testSimplification() {
        Action a;

        // Test the ordering
        a = Action.parse("x(a) point");
        assertEquals("point x(a)", a.simplify().toString());
        a = Action.parse("bin(a) x(a)");
        assertEquals("x(a) bin(a)", a.simplify().toString());

        // Test we accumulate nicely
        a = Action.parse("y(b) bin(a) bin(b) y(a) point");
        assertEquals("point y(b, a) bin(a, b)", a.simplify().toString());

        // test dropping unnecessary stuff
        a = Action.parse("line chord point treemap edge");
        assertEquals("edge treemap", a.simplify().toString());
        a = Action.parse("legends(none) axes(y) at(0,1) axes(x) at(1,2,3,4) legends(all)");
        assertEquals("axes(x) legends(all) at(1, 2, 3, 4)", a.simplify().toString());
        a = Action.parse("x(a) y(b) mean(b)");
        assertEquals("x(a) y(b) mean(b)", a.simplify().toString());
    }

    @Test
    public void testDataSimplification() {
        Action a;

        // Test the ordering
        a = Action.parse("x(a) data('foo.csv')");
        assertEquals("data('foo.csv') x(a)", a.simplify().toString());

        // Test the ordering
        a = Action.parse("data('a.csv') x(a) data('foo.csv')");
        assertEquals("data('foo.csv') x(a)", a.simplify().toString());

    }

}
