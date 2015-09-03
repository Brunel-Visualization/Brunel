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

package org.brunel.model.style;

import org.brunel.data.Data;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StylesTest {

    @Test
    public void testCaching() {
        // It is the same instance
        assertEquals(StyleFactory.instance(), StyleFactory.instance());

        StyleFactory factory = StyleFactory.instance();
        factory.selectors.clear();

        // Check size
        assertEquals(400, factory.maxEntries);

        // Check that it adds 200
        for (int i = 0; i < 200; i++) factory.makeSelectors("p" + i);
        assertEquals(200, factory.selectors.size());

        // Check it is caching
        StyleSelector p200 = factory.makeSelectors("p200")[0];
        StyleSelector p200Again = factory.makeSelectors("p200")[0];
        assertEquals(p200, p200Again);

        // Fill the cache and make sure it wiped out the right ones
        for (int i = 200; i < 500; i++) factory.makeSelectors("p" + i);
        assertEquals(400, factory.selectors.size());

        for (int i = 0; i < 100; i++) assertFalse(factory.selectors.containsKey("p" + i));
        for (int i = 100; i < 500; i++) assertTrue(factory.selectors.containsKey("p" + i));
    }

    @Test
    public void testHierarchyItemParsing() {
        StyleFactory factory = StyleFactory.instance();

        assertEquals("{foo classes=[]} > {bar classes=[]}", debug(factory.makeSelectors("foo bar")));
        assertEquals("foo bar", str(factory.makeSelectors("foo bar")));

        assertEquals("{foo classes=[bar]} > {something classes=[]}", debug(factory.makeSelectors("foo.bar \tsomething.")));
        assertEquals("foo.bar something", str(factory.makeSelectors("foo.bar\tsomething")));
    }

    private String debug(StyleSelector[] sel) {
        String[] result = new String[sel.length];
        for (int i = 0; i < result.length; i++) result[i] = sel[i].debug();
        return Data.join(result, " | ");
    }

    private String str(StyleSelector[] sel) {
        String[] result = new String[sel.length];
        for (int i = 0; i < result.length; i++) result[i] = sel[i].toString();
        return Data.join(result, ", ");
    }

    @Test
    public void testMatching() {
        StyleFactory factory = StyleFactory.instance();

        StyleSelector selA = factory.makeSingleSelector("a");
        StyleSelector selDotX = factory.makeSingleSelector(".x");
        StyleSelector selADotX = factory.makeSingleSelector("a.x");
        StyleSelector sel2Levels = factory.makeSingleSelector(".y a");

        StyleTarget a = new StyleTarget("a", null);
        StyleTarget aDotX = new StyleTarget("a", null, "x");
        StyleTarget aDotY = new StyleTarget("a", null, "y");
        StyleTarget aDotXY = new StyleTarget("a", null, "y", "x");
        StyleTarget bDotXY = new StyleTarget("b", null, "y", "x");
        StyleTarget bDotXYADotX = new StyleTarget("a", bDotXY, "x");

        assertEquals(true, selA.match(a));
        assertEquals(true, selA.match(aDotX));
        assertEquals(true, selA.match(aDotY));
        assertEquals(true, selA.match(aDotXY));
        assertEquals(false, selA.match(bDotXY));
        assertEquals(true, selA.match(bDotXYADotX));

        assertEquals(false, selDotX.match(a));
        assertEquals(true, selDotX.match(aDotX));
        assertEquals(false, selDotX.match(aDotY));
        assertEquals(true, selDotX.match(aDotXY));
        assertEquals(true, selDotX.match(bDotXY));
        assertEquals(true, selDotX.match(bDotXYADotX));

        assertEquals(false, selADotX.match(a));
        assertEquals(true, selADotX.match(aDotX));
        assertEquals(false, selADotX.match(aDotY));
        assertEquals(true, selADotX.match(aDotXY));
        assertEquals(false, selADotX.match(bDotXY));
        assertEquals(true, selADotX.match(bDotXYADotX));

        assertEquals(false, sel2Levels.match(a));
        assertEquals(false, sel2Levels.match(aDotX));
        assertEquals(false, sel2Levels.match(aDotY));
        assertEquals(false, sel2Levels.match(aDotXY));
        assertEquals(false, sel2Levels.match(bDotXY));
        assertEquals(true, sel2Levels.match(bDotXYADotX));

        //  a b.x a
        StyleTarget abDotXa = new StyleTarget("a", new StyleTarget("b", a, "x"));

        assertEquals(true, factory.makeSingleSelector("a a").match(abDotXa));
        assertEquals(true, factory.makeSingleSelector("b a").match(abDotXa));
        assertEquals(true, factory.makeSingleSelector(".x a").match(abDotXa));
        assertEquals(true, factory.makeSingleSelector("b.x a").match(abDotXa));
        assertEquals(false, factory.makeSingleSelector("a.x a").match(abDotXa));
        assertEquals(false, factory.makeSingleSelector("a a a").match(abDotXa));
        assertEquals(false, factory.makeSingleSelector("b a a").match(abDotXa));
        assertEquals(false, factory.makeSingleSelector("a b").match(abDotXa));
        assertEquals(true, factory.makeSingleSelector("a .x a").match(abDotXa));
    }

    @Test
    public void testMultiItemParsing() {
        StyleFactory factory = StyleFactory.instance();

        assertEquals("ANY > {classes=[p]} | {a classes=[]} > {label classes=[q]} | {classes=[red,green]}",
                debug(factory.makeSelectors("* .p,  a label.q  , .red.green")));
        assertEquals("* .p, a label.q, .red.green", str(factory.makeSelectors("* .p,  a label.q  , .red.green")));
    }

    @Test
    public void testOrder() {
        StyleSheet s = StyleFactory.instance().makeStyleSheet("a {x:1} a {x:2}");
        assertEquals("2", s.stylesFor(null, "a").get("x"));
    }

    @Test
    public void testSingleItemParsing() {
        StyleFactory factory = StyleFactory.instance();

        assertEquals("{foo classes=[]}", debug(factory.makeSelectors("  foo")));
        assertEquals("foo", str(factory.makeSelectors("foo")));

        assertEquals("ANY", debug(factory.makeSelectors("  *\t")));
        assertEquals("*", str(factory.makeSelectors("*")));

        assertEquals("{foo classes=[bar]}", debug(factory.makeSelectors("foo.bar ")));
        assertEquals("foo.bar", str(factory.makeSelectors("foo.bar")));

        assertEquals("{foo classes=[bar,gee]}", debug(factory.makeSelectors("foo.bar.gee ")));
        assertEquals("foo.bar.gee", str(factory.makeSelectors("foo.bar.gee")));

        assertEquals("{classes=[bar]}", debug(factory.makeSelectors(".bar ")));
        assertEquals(".bar", str(factory.makeSelectors(".bar")));
    }

}
