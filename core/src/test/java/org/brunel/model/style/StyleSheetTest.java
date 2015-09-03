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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StyleSheetTest {

    private static final String SAMPLE = ""
            + "background {fill:#ffffff; pad:5; outline:#ffffff00} coordinates {fill:#ffffff00} "
            + "axis { fill:#666} axis.title {pad:2; font-weight:bold} axis.mark {fill:#fff0} axis.tick {font-size: 12px} axis.grid {visible:false} axis.box {fill:#f9ecb7}"
            + "legend { fill:#666666} legend.title {font-size: 14px; fill:#00669E; pad:2; font-weight:bold} legend.label {font-size: 12px} "
            + "inner { fill:white; stroke-width:0} inner-0 { visibility:hidden } inner-1 { fill:#f9ecb7 } inner-2 { fill:#d3870d } "
            + "* { font-family:Helvetica Neue; font-size: 14px; stroke-width:0.5} "
            + "element {fill: #0B9BDBD0; outline:darker; stroke-width:0.25} line {stroke-width:2} path {stroke-width:2} text {font-size:20px; stroke:none} label {font-size:12px; fill:black; stroke:none}";

    @Test
    public void testWriting() {
        StyleSheet sheet = StyleFactory.instance().makeStyleSheet("a {x:1; y:2} .c {x:2; z:4}");
        assertEquals("a {\n" +
                "\ty: 2 !important;\n" +
                "\tx: 1 !important;\n" +
                "}\n" +
                "\n" +
                ".c {\n" +
                "\tz: 4 !important;\n" +
                "\tx: 2 !important;\n" +
                "}\n", sheet.toString());

        String output = StyleFactory.instance().makeStyleSheet(SAMPLE).toString("top");
        assertTrue(output.startsWith("top * {"));

        // Check we ignore errors
        String good = StyleFactory.instance().makeStyleSheet("a {x:1}").toString();
        assertEquals(good, StyleFactory.instance().makeStyleSheet("a {x:1} b ").toString());
        assertEquals(good, StyleFactory.instance().makeStyleSheet("a {x:1} {} ").toString());
        assertEquals(good, StyleFactory.instance().makeStyleSheet("{} a {x:1}").toString());
        assertEquals(good, StyleFactory.instance().makeStyleSheet("a {x:1; y}").toString());
        assertEquals(good, StyleFactory.instance().makeStyleSheet("a {x:1; :4}").toString());
        assertEquals(good, StyleFactory.instance().makeStyleSheet("a {x:1; :a:b,}").toString());
    }

}
