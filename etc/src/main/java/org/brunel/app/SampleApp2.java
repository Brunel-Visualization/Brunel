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

package org.brunel.app;

import org.brunel.action.Action;
import org.brunel.build.VisualizationBuilder;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.model.VisItem;
import org.brunel.util.PageOutput;

import java.io.StringWriter;

/**
 * A demonstration application that shows hwo to use Brunel rapidly
 */
public class SampleApp2 {

    /**
     * A very simple sample program which shows how to run brunel on data you already have in memory
     */
    public static void main(String[] args) throws Exception {
        // Process the commands:

        String command = "bar x(Region) y(#count) sort(#count) tooltip(#all)";

        Field regionField = Fields.makeColumnField("Region", "Data Regions", new String[] {
                "a", "b", "a", "a" , "c"
        });

        Dataset data = Dataset.make(new Field[] { regionField});

        // Build the action and apply it to create the VisItem
        Action action = Action.parse(command);
        VisItem vis = action.apply(data);

        // Define a builder using default options
        BuilderOptions options = new BuilderOptions();
        VisualizationBuilder builder = VisualizationBuilder.make(options);

        // Build the visualization into a 600x600 area
        builder.build(vis, 600, 600);

        // output to standard out
        StringWriter writer = new StringWriter();
        new PageOutput(builder, writer).write();
        System.out.println(writer.toString());

    }
}
