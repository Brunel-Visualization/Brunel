
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

package org.brunel.app;

import org.brunel.action.Action;
import org.brunel.build.d3.D3Builder;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.brunel.model.VisItem;
import org.brunel.util.WebDisplay;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Scanner;

public class AnimationSample {

//            public static final String COMMAND = "bar treemap x(origin, year, mpg) color(year) size(weight) sum(weight) bin(year, mpg) label(mpg) tooltip(#all)";
//    public static final String COMMAND = " x(year) y(horsepower) color(#selection)| bar x(cylinders) y(#count) mean(mpg) bin(cylinders)";
//    public static final String COMMAND = " bar x(origin) y(#count) color(#selection)";
    private static final String COMMAND = " x(year) color(cylinders) y(mpg) sum(mpg) label(mpg) tooltip(#all) bar stack";
//    private static final String COMMAND = "bubble x(year) color(cylinders) size(mpg) mean(mpg) label(mpg) tooltip(#all)";
//    public static final String COMMAND = "chord x(origin) y(cylinders) color(origin) label(cylinders) size(weight) sum(weight) tooltip(#all)";

    private static final Dataset data = Dataset.make(readResourceAsCSV("Cars.csv"));

    public static void main(String[] args) {

        String text = args.length > 0 ? args[0] : COMMAND;

        Action action = Action.parse(text);
        VisItem item = action.apply(data);

        BuilderOptions options = new BuilderOptions();
        options.visIdentifier = "vis";
        options.className = "Vis";
        options.generateBuildCode = false;
        D3Builder builder = D3Builder.make(options);
        builder.build(item, 800, 600);
        AnimationSample sample = new AnimationSample("Animation Samples", text, builder);
        sample.showInBrowser("index.html");

    }

    private void showInBrowser(String fileName) {
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(new File(displayBaseDir, fileName).toURI());
        } catch (Throwable ex) {
            // Silently fail
        }
    }

    private static Field[] readResourceAsCSV(String resourceName) {
        InputStream stream = AnimationSample.class.getResourceAsStream("data-csv/" + resourceName);
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        String s = scanner.hasNext() ? scanner.next() : "";
        return CSV.read(s);
    }

    private final File displayBaseDir;

    /* Pass in the subdirectory name to util */
    private AnimationSample(String dirName, String action, D3Builder builder) {
        WebDisplay display = new WebDisplay("Animation Sample");
        displayBaseDir = display.makeDir(dirName);

        String css = builder.getStyleOverrides();
        String js= (String) builder.getVisualization();
        String imports = builder.makeImports();

        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"UTF-8\"><title>Animation Sample</title>\n");
        html.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../out/BrunelBaseStyles.css\">\n");
        html.append(imports);
        html.append("\n<style>\n").append(css).append("\n</style>\n");
        html.append("</head><body>\n<h1>").append(action).append("</h1>\n");
        html.append("<svg id=\"vis\" width=800 height=600></svg>\n");
        html.append("<hr/>\n");
        html.append("<button onclick=\"randomSwaps();\">Randomize</button>");
        html.append("<button onclick=\"randomSwaps('size');\">Randomize Size</button>");
        html.append("<button onclick=\"filter('x');\">Filter X</button>");
        html.append("<button onclick=\"filter('y');\">Filter Y</button>");
        html.append("<button onclick=\"filter('color');\">Filter Color</button>");
        html.append("<button onclick=\"filter('size');\">Filter Size</button>");
        html.append("<button onclick=\"stop();\">STOP</button>");
        html.append("\n<script>\n").append(js).append("\n");

        addDriverCode(html);

        html.append("</script></body></html>\n");

        writeToFile("index.html", html.toString());
    }

    private void addDriverCode(StringBuilder html) {
        html.append("var anim=200, id=null, brunel=Vis('vis');\n");
        html.append("brunel.build(table1);\n\n");

        html.append("function run(f) { if (id) stop(); id = setInterval(function() {f(); brunel.data(table1); brunel.rebuild(150);}, anim) };\n");
        html.append("function stop() { clearInterval(id); id = null};\n");

        html.append("function randomSwaps(fType) {\n");
        html.append("  var element = brunel.charts[0].elements[0];\n");
        html.append("  var fieldName = element.fields[fType];\n");
        html.append("  var index = -1;\n");
        html.append("  if (fieldName) { index=0; while (element.data().fields[index].name != fieldName) index++; }\n");
        html.append("  run(function() {\n");
        html.append("     for (var i=0; i<30; i++) {\n");
        html.append("       var a = Math.floor(Math.random()*(table1.length-1)) + 1;\n");
        html.append("       var b = Math.floor(Math.random()*(table1.length-1)) + 1;\n");
        html.append("       var c = index >= 0 ? index : Math.floor(Math.random()*(table1[0].length));\n");
        html.append("       var t = table1[a][c]; table1[a][c] = table1[b][c]; table1[b][c] = t;\n");
        html.append("     }\n");
        html.append("  });\n");
        html.append("};\n\n");

        html.append("function filter(fType) {\n");
        html.append("  var element = brunel.charts[0].elements[0];\n");
        html.append("  var fieldName = element.fields[fType];\n");
        html.append("  if (!fieldName) return; else fieldName = fieldName[0];\n");
        html.append("  var field = element.data().field(fieldName);\n");
        html.append("  var start = 0, increment=0.1, window=0.2;\n");
        html.append("  run(function() {\n");
        html.append("       if (start+window > 1) start = 0;\n");
        html.append("       var a = field.min() + (field.max()-field.min())*start;\n");
        html.append("       var b = field.min() + (field.max()-field.min())*(start+window);\n");
        html.append("       var command = fieldName + ' in ' + a + ',' + b;\n");
        html.append("       brunel.dataPreProcess(function(data) { return data.filter(command)});\n");
        html.append("       brunel.build();\n");
        html.append("       start += increment;\n");
        html.append("  });\n");
        html.append("};\n");

    }

    private void writeToFile(String fileName, String html) {
        try {
            File file = new File(displayBaseDir, fileName);
            PrintWriter item = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
            item.println(html);
            item.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
