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

package org.brunel.build.element;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.brunel.action.Param;
import org.brunel.build.ScaleBuilder;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;

/**
 * Overrides element builder to build guides only
 */
class GuideElementBuilder extends CoordinateElementBuilder {

  private static final boolean DRAGGABLE_GUIDES = false;

  private static final Set<String> MATH_FUNCTIONS = new HashSet<>(
    Arrays.asList("e pi abs acos asin atan atan2 ceil cos exp floor log max min pow random round sin sqrt tan".split(" ")));
  private static final Set<Character> SYMBOLS = new HashSet<>(
    Arrays.asList('(', ')', '*', '-', '+', '/', '?', '=', '!', '>', '<', ':'));

  public GuideElementBuilder(ElementStructure structure, ScriptWriter out, ScaleBuilder scalesBuilder) {
    super(structure, out, scalesBuilder);
  }

  public void writePerChartDefinitions() {
    super.writePerChartDefinitions();
    if (DRAGGABLE_GUIDES) {
      out.add("function make_draggable(info, scx, scy, buildFunc, that) {").indentMore()
        .onNewLine().add("var x0, y0, x1, y1, ox, oy").endStatement()
        .onNewLine().add("return d3.drag()")
        .addChained("on('start', function() { ox = info.x; oy = info.y; x0 = scx.invert(d3.event.x); y0 = scy.invert(d3.event.y) })")
        .addChained("on('drag', function() {x1 = scx.invert(d3.event.x); y1 = scy.invert(d3.event.y); info.x=ox + x1-x0;  info.y = oy + y1-y0; buildFunc.call(that)})")
        .endStatement()
        .indentLess().onNewLine().add("}").endStatement();
    }
  }

  public void generate() {

    List<Param> guides = vis.tGuides;

    int steps = getSteps(guides);

    // Define the variables and domains we will need
    out.add("var i, x, y, t, selection, path, data = [], ")
      .onNewLine().add("xDomain = scale_x.domain(), x0 = xDomain[0], xs = xDomain[xDomain.length-1]-x0, ")
      .onNewLine().add("yDomain = scale_y.domain(), y0 = yDomain[0], ys = xDomain[yDomain.length-1]-y0").endStatement();

    // Generate the data for the guide functions
    out.add("var guideData = Array.apply(null, Array(" + steps + ")).map(function (v, i) {")
      .indentMore().onNewLine()
      .add("t = i /", (steps - 1), ";", "return {x:x0 + xs*t, y:y0 + ys*t, t:t} })").endStatement();

    setGeometry();                           // And the coordinate definitions

    out.add("this._drags = this._drags || [");
    for (int i = 0; i < guides.size(); i++) {
      if (i > 0) {
        out.add(", ");
      }
      out.add("{x:0,y:0}");
    }
    out.add("]").comment("Stores the persistent drag info");

    for (int index = 0; index < guides.size(); index++) {
      Param p = guides.get(index);

      out.onNewLine().ln().comment("Defining guide #" + index).onNewLine();

      String defX = definition(p, "x");                   // Defines X
      String defY = definition(p, "y");                   // Defines Y

      out.add("var drags = this._drags[" + index + "]").endStatement();

      // Define the path, adding in the selection state for the offset
      out.add("path = d3.line().curve(d3.curveCatmullRom)")
        .addChained("x(function(d) { \nreturn scale_x(" + defX + "+ drags.x) \n })")
        .addChained("y(function(d) { return scale_y(" + defY + "+ drags.y)  })")
        .endStatement();

      // define the labeling structure to be used later
      defineLabelSettings(structure.details);
      defineLabeling(structure.details);

      out.add("selection = main.selectAll('.element.guide" + index + "').data(['guide'])");
      out.endStatement();

      out.add("var added = selection.enter().append('path').attr('class', 'element line guide guide" + index + "')");
      if (structure.chart.accessible) {
        out.addChained("attr('role', 'img').attr('aria-label', 'reference guide')");
      }
      out.add(",").ln().indent().add("merged = selection.merge(added)").endStatement();

      out.add("BrunelD3.transition(merged, transitionMillis)")
        .addChained("attr('d', path(guideData))");
      if (DRAGGABLE_GUIDES) {
        out.addChained("call(make_draggable(drags, scale_x, scale_y, build, this))");
      }
      out.endStatement();
      if (structure.needsLabels()) {
        out.add("label(merged, transitionMillis)").endStatement();
      }
    }

  }

  // Returns the definition of the given coord (which defaults to the defined value)
  private String definition(Param p, String coord) {
    if (p.asString().equals(coord)) {
      return sanitize(p.firstModifier().asString());
    } else {
      return "d." + coord;
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

  private String sanitize(String text) {
    try {
      // We cannot just use this string as it could be insecure, so we tokenize it and only accept a subset
      StreamTokenizer in = new StreamTokenizer(new StringReader(text));
      for (Character c : SYMBOLS) {
        in.ordinaryChar(c);
      }
      StringBuilder out = new StringBuilder();

      for (; ; ) {
        int tok = in.nextToken();
        if (tok == StreamTokenizer.TT_EOF || tok == StreamTokenizer.TT_EOL) {
          break;
        }
        if (tok == StreamTokenizer.TT_NUMBER) {
          out.append(in.nval);
        } else if (tok == StreamTokenizer.TT_WORD) {
          String s = in.sval.toLowerCase();
          if (s.equals("x") || s.equals("y") || s.equals("t"))        // Known x,y,t defined variables
          {
            out.append("d.").append(s);                             // x -> d.x, etc.
          } else if (MATH_FUNCTIONS.contains(s))                        // Math functions
          {
            out.append("Math.").append(s);
          } else {
            throw new IllegalStateException("Cannot use term '" + s + "' in a function definition");
          }
        } else {
          char c = (char) tok;
          if (SYMBOLS.contains(c)) {
            out.append(c);
          } else {
            throw new IllegalStateException("Cannot use symbol '" + c + "' in a function definition");
          }
        }
      }
      return out.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
