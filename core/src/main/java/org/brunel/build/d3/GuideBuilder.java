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
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

        List<Param> guides = vis.tGuides;

        int steps = getSteps(guides);

        // Define the variables and domains we will need
        out.add("var i, x, y, t, selection, path, data = [], ")
                .onNewLine().add("xDomain = scale_x.domain(), x0 = xDomain[0], xs = xDomain[xDomain.length-1]-x0, ")
                .onNewLine().add("yDomain = scale_y.domain(), y0 = yDomain[0], ys = xDomain[yDomain.length-1]-y0").endStatement();

        // Generate the data for the guide functions
        out.add("var guideData = Array.apply(null, Array(" + steps + ")).map(function (v, i) {")
                .indentMore().onNewLine()
                .add("t = i /", (steps-1), ";", "return {x:x0 + xs*t, y:y0 + ys*t, t:t} })").endStatement();

        int index = 0;

        ElementDetails details = makeDetails();         // Create the details of what the element should be
        setGeometry(details);                           // And the coordinate definitions

        for (Param p : guides) {
            index++;
            out.onNewLine().ln().comment("Defining guide #" + index).onNewLine();

            String defX = definition(p, "x");                   // Defines X
            String defY = definition(p, "y");                   // Defines Y

            // Define the path
            out.add("path = d3.line().curve(d3.curveCatmullRom)")
                    .addChained("x(function(d) { return scale_x(", defX, ") })")
                    .addChained("y(function(d) { return scale_y(", defY, ") })")
                    .endStatement();

            // define the labeling structure to be used later
            defineLabeling(details);


            out.add("selection = main.selectAll('.element.guide" + index + "').data(guideData)").endStatement();

            out.add("selection.enter().append('path').attr('class', 'element line guide guide" + index + "')");
            if (structure.chart.accessible)
                out.addChained("attr('role', 'img').attr('aria-label', 'reference guide')");
            out.endStatement();

            out.add("BrunelD3.transition(selection, true, transitionMillis)")
                    .addChained("attr('d', path(guideData))")
                    .endStatement();


            writeCoordinateLabelingAndAesthetics(details, false);

        }

    }

    private int getSteps(List<Param> guides) {
        int max = 0;
        for (Param p : guides) {
            if (p.modifiers().length > 1) {
                try {
                    max = Math.max((int) p.modifiers()[1].asDouble(), max);
                } catch (Exception e) {
                    throw new IllegalArgumentException("When setting number of steps in a guide, the value must be an integer");
                }
            }
        }
        return max <= 1 ? 40 : max;
    }

    // Returns the definition of the given coord (which defaults to the defined value)
    private String definition(Param p, String coord) {
        if (p.asString().equals(coord)) {
            return sanitize(p.firstModifier().asString());
        } else {
            return "d." + coord;
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
                        out.append("d.").append(s);                             // x -> d.x, etc.
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
