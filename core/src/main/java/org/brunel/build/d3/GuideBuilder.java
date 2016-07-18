/*
 * Copyright (c) 2016 IBM Corporation and others.
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

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.d3.element.D3ElementBuilder;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Overrides element builder to build guides only
 */
public class GuideBuilder extends D3ElementBuilder {

    private static final Set<String> MATH_FUNCTIONS = new HashSet<>(
            Arrays.asList("e pi abs acos asin atan atan2 ceil cos exp floor log max min pow random round sin sqrt tan".split(" ")));
    private static final Set<Character> SYMBOLS = new HashSet<>(
            Arrays.asList('(', ')', '*', '-', '+', '/', '?', '=', '!', '>', '<', ':'));

    public GuideBuilder(ElementStructure structure, ScriptWriter out, D3ScaleBuilder scalesBuilder, D3Interaction interaction) {
        super(structure, out, scalesBuilder, interaction);
    }

    public void generate(int elementIndex) {

        // Define the variables and domains we will need
        out.add("var i, x, y, t, selection, path, data = [], ")
                .onNewLine().add("xDomain = scale_x.domain(), x0 = xDomain[0], x1 = xDomain[xDomain.length-1], ")
                .onNewLine().add("yDomain = scale_y.domain(), y0 = yDomain[0], y1 = xDomain[yDomain.length-1]").endStatement();

        // Generate the data for the guide functions
        out.add("for (i=0; i<=300; i++) {").indentMore().onNewLine();
        out.add("t = i/300").endStatement();
        out.add("x = x0 + (x1-x0)*t").endStatement();
        out.add("y = y0 + (y1-y0)*t").endStatement();
        out.add("data[i] = {").indentMore();

        // Define the data x1, y1 ... yN, yN for each  of the N parameters
        int index = 0;
        for (Param p : vis.tGuides) {
            index++;
            if (index > 1) out.add(", ");
            out.onNewLine().add("x" + index + ":" + definition(p, "x") + ", y" + index + ":" + definition(p, "y"));
        }
        out.indentLess().onNewLine().add("}")
                .indentLess().onNewLine().add("}").endStatement();

        index = 0;
        for (Param p : vis.tGuides) {
            index++;

            // Define the path
            out.add("path = d3.svg.line().x(function(d) {return scale_x(d.x" + index + ")}).y(function(d) {return scale_y(d.y" + index + ")})")
                    .endStatement();

            out.add("selection = main.selectAll('.element.guide" + index + "').data(data)").endStatement();

            out.add("selection.enter().append('path').attr('class', 'element line guide guide" + index + "')");
            if (structure.chart.accessible)
                out.addChained("attr('role', 'img').attr('aria-label', 'reference guide')");
            out.endStatement();

            out.add("BrunelD3.trans(selection,transitionMillis)")
                    .addChained("attr('d', path(data))")
                    .endStatement();
        }

    }

    // Returns the definition of the given coord (which defaults to the defined value)
    private String definition(Param p, String coord) {
        if (p.asString().equals(coord)) {
            return sanitize(p.firstModifier().asString());
        } else {
            return coord;
        }
    }

    private String sanitize(String text) {
        try {
            // We cannot just use this string as it could be insecure, so we tokenize it and only accept a subset
            StreamTokenizer in = new StreamTokenizer(new StringReader(text));
            for (Character c : SYMBOLS) in.ordinaryChar(c);
            StringBuilder out = new StringBuilder();

            for (; ; ) {
                int tok = in.nextToken();
                if (tok == StreamTokenizer.TT_EOF || tok == StreamTokenizer.TT_EOL) break;
                if (tok == StreamTokenizer.TT_NUMBER) out.append(in.nval);
                else if (tok == StreamTokenizer.TT_WORD) {
                    String s = in.sval.toLowerCase();
                    if (s.equals("x") || s.equals("y") || s.equals("t"))        // Known x,y,t defined variables
                        out.append(s);
                    else if (MATH_FUNCTIONS.contains(s))                        // Math functions
                        out.append("Math.").append(s);
                    else throw new IllegalStateException("Cannot use term '" + s + "' in a function definition");
                } else {
                    char c = (char) tok;
                    if (SYMBOLS.contains(c))
                        out.append(c);
                    else throw new IllegalStateException("Cannot use symbol '" + c + "' in a function definition");
                }
            }
            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
