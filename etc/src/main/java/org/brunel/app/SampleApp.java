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
import org.brunel.build.VisualizationBuilder;
import org.brunel.build.util.BuilderOptions;
import org.brunel.model.VisItem;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * A demonstration application that shows hwo to use Brunel rapidly
 */
public class SampleApp {

    /*
     * Run a simple Brunel command.
     * The first parameter is a Brunel Command. The second is the URL of the data source. This can be a file reference
     * such as 'file://...'.
     * When run it will build a HTML page with the full Brunel in it, and display it in the default browser
     * @param args specify the command
     */
    public static void main(String[] args) throws Exception {

        // Process the commands:
        String command = "bubble size(#count) color(country) label(country, #count) tooltip(brand) list(brand) " +
                "style ('.background {fill:#222244}')";
        if (args.length > 0) command = args[0];

        String source = "http://brunel.mybluemix.net/sample_data/whiskey.csv";
        if (args.length > 1) source = args[1];

        // Assemble the two parts into one complete brunel command, with data and visualziation actions
        String fullCommand = "data('" + source + "') " + command;

        // Build the action and apply it to create the VisItem
        Action action = Action.parse(fullCommand);
        VisItem vis = action.apply();

        // Define a builder using online library version 0.7
        BuilderOptions options = new BuilderOptions();
        options.version = "2.6";
        VisualizationBuilder builder = VisualizationBuilder.make(options);

        // Build the visualization into a 600x600 area
        builder.build(vis, 600, 600);

        // Write the results out as a HTML page
        File file = new File("BrunelSample.html");
        PrintWriter out = new PrintWriter(new FileWriter(file));

        out.println("<HTML><HEAD>");
        out.println("<title>Brunel SampleApp Output</title>");

        // Add in style sheet definitions
        out.println(builder.makeStyleSheets());

        String css = builder.getStyleOverrides();
        if (!css.isEmpty()) {
            out.println("\t<style>\n\t\t" + css + "\n\t</style>\n");
        }

        out.println("</HEAD><BODY>");
        out.println("<svg id=\"visualization\" width=\"600\" height=\"600\"></svg>");

        // Add in the imports needed
        out.println(builder.makeImports());
        out.println("<script>");

        // Add in the Javascript generated
        out.println(builder.getVisualization());
        out.println("</script>");
        out.println("</BODY></HTML>");

        // Finish up and show the results
        out.close();
        Desktop.getDesktop().browse(file.toURI());

    }
}
