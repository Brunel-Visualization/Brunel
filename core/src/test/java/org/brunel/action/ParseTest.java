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

package org.brunel.action;

import org.brunel.action.Param.Type;
import org.brunel.action.Parser.BrunelToken;
import org.brunel.data.Data;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ParseTest {

    @Test
    public void testAsField() {
        assertEquals("foo", new Parser().parseField("foo", "err").toString());
        assertEquals("f123", new Parser().parseField("f123", "err").toString());
        assertEquals("f_1", new Parser().parseField("f_1", "err").toString());
        assertEquals("_1", new Parser().parseField("_1", "err").toString());
        assertEquals("#row", new Parser().parseField("#row", "err").toString());
        assertEquals("#series", new Parser().parseField("#SERIES", "err").toString());
        assertEquals("#values", new Parser().parseField("#Values", "err").toString());
        assertEquals("#count", new Parser().parseField("#COUnt", "err").toString());

        try {
            new Parser().parseField((" foo"), "err");
            fail("Should not have parsed");
        } catch (IllegalStateException ignored) {

        }

        try {
            new Parser().parseField(("fo o"), "err");
            fail("Should not have parsed");
        } catch (IllegalStateException ignored) {

        }

        try {
            new Parser().parseField(("1"), "err");
            fail("Should not have parsed");
        } catch (IllegalStateException ignored) {

        }

        try {
            new Parser().parseField(("foo:1"), "err");
            fail("Should not have parsed");
        } catch (IllegalStateException ignored) {

        }
    }

    @Test
    public void testInvalidActions() {
        try {
            new Parser().getActionSteps(new Parser().tokenize("edgyThing"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("bin point"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("bin() point"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("point()"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("bin(#foo)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("at(#count)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("style(12)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("style(red)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("style('a', 'b"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("axes()"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("axes(yeah)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("axes('none')"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("axes(1)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

        try {
            new Parser().getActionSteps(new Parser().tokenize("axes(#row)"));
            fail("Should not have parsed");
        } catch (Exception ignored) {
        }

    }

    @Test
    public void testTokenize() {
        List<BrunelToken> tokens;

        tokens = new Parser().tokenize("this is     a\nsimple\tstring");
        assertEquals("4,2,1,6,6", lengths(tokens));

        tokens = new Parser().tokenize("4.0 n=3 n:3");
        assertEquals("3,3,1,1,1", lengths(tokens));

        tokens = new Parser().tokenize("main(1, b, 12.0)c(3)d()");
        assertEquals("4,1,1,1,1,1,4,1,1,1,1,1,1,1,1", lengths(tokens));

        tokens = new Parser().tokenize("(:,\n))");
        assertEquals("1,1,1,1,1", lengths(tokens));

        tokens = new Parser().tokenize("' a long string with quotes \\' and \\\"'");
        assertEquals("38", lengths(tokens));

        tokens = new Parser().tokenize("aaaa(bb:ccc)");
        assertEquals("4,1,2,1,3,1", lengths(tokens));

    }

    private String lengths(List<BrunelToken> tokens) {
        String r = "";
        for (BrunelToken s : tokens) {
            if (r.length() > 0) r += ",";
            r += s.content.length();
        }
        return r;
    }

    @Test
    public void testValidActions() {
        List<ActionStep> actions;

        actions = new Parser().getActionSteps(new Parser().tokenize("point line chord"));
        assertEquals(3, actions.size());
        assertEquals("point", actions.get(0).toString());
        assertEquals("line", actions.get(1).toString());
        assertEquals("chord", actions.get(2).toString());

        actions = new Parser().getActionSteps(new Parser().tokenize("point bin(a,b,#count)"));
        assertEquals(2, actions.size());
        assertEquals("point", actions.get(0).toString());
        assertEquals("bin(a, b, #count)", actions.get(1).toString());
        assertEquals(3, actions.get(1).parameters.length);

        actions = new Parser().getActionSteps(new Parser().tokenize("at(1.0, 1e2, 22.90)"));
        assertEquals(1, actions.size());
        assertEquals("at(1, 100, 22.9)", actions.get(0).toString());

        actions = new Parser().getActionSteps(new Parser().tokenize("sort(color:ascending) bin(color:12)"));
        assertEquals(2, actions.size());
        assertEquals("sort(color:ascending)", actions.get(0).toString());
        assertEquals("bin(color:12)", actions.get(1).toString());
        assertEquals(1, actions.get(1).parameters.length);
        assertEquals(12.0, actions.get(1).parameters[0].firstModifier().asDouble(), 1e-9);

        actions = new Parser().getActionSteps(new Parser().tokenize("axes(none) style(\"foo\") legends(auto)"));
        assertEquals(3, actions.size());
        assertEquals("axes(none)", actions.get(0).toString());
        assertEquals("style('foo')", actions.get(1).toString());
        assertEquals("legends(auto)", actions.get(2).toString());

        actions = new Parser().getActionSteps(new Parser().tokenize("bin(\na:2,                   b: 3)"));
        assertEquals(1, actions.size());
        assertEquals("bin(a:2, b:3)", actions.get(0).toString());
    }

    @Test
    public void testListCommands() {
        List<ActionStep> actions;
        actions = new Parser().getActionSteps(new Parser().tokenize("color(a:[red,green,blue])"));
        assertEquals(1,actions.size());
        ActionStep a = actions.get(0);
        assertEquals("color", a.name);
        assertEquals(1, a.parameters.length);
        Param p = a.parameters[0];
        assertEquals(Type.field, p.type());

        Param[] mods = p.modifiers();
        assertEquals(1, mods.length);
        assertEquals(Type.list, mods[0].type());
        List<Param> items = mods[0].asList();
        assertEquals("red, green, blue", Data.join(items));

        // Check that a-b-c is simply syntacic sugar for [a,b,c]
        actions = new Parser().getActionSteps(new Parser().tokenize("color(a:red-green-blue)"));
        items = actions.get(0).parameters[0].modifiers()[0].asList();
        assertEquals("red, green, blue", Data.join(items));
    }

    @Test
    public void testEmptyListCommand() {
        List<ActionStep> actions = new Parser().getActionSteps(new Parser().tokenize("color(a:[])"));
        Param[] mods = actions.get(0).parameters[0].modifiers();
        assertEquals(1, mods.length);
        assertEquals(Type.list, mods[0].type());
        List<Param> items = mods[0].asList();
        assertEquals(0, items.size());
    }

	@Test
	public void testMissingValuesListCommand() {
		List<ActionStep> actions = new Parser().getActionSteps(new Parser().tokenize("color(a:[,6,,8,,])"));
		Param[] mods = actions.get(0).parameters[0].modifiers();
		assertEquals(1, mods.length);
		assertEquals(Type.list, mods[0].type());
		List<Param> items = mods[0].asList();
		assertEquals(6, items.size());

		assertEquals(null, items.get(0));
		assertEquals(6, items.get(1).asInteger());
		assertEquals(null, items.get(2));
		assertEquals(8, items.get(3).asInteger());
		assertEquals(null, items.get(4));
		assertEquals(null, items.get(5));
	}



}
