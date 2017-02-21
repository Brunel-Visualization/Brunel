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
import org.brunel.build.util.DataCache;
import org.brunel.build.util.SimpleCache;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.model.VisItem;
import org.brunel.util.PageOutput;

import java.io.StringWriter;

/**
 * A demonstration application that shows hwo to use Brunel rapidly
 */
public class SampleAppTwoDataSets {

	private static final SimpleCache cache = new SimpleCache();

	/**
	 * A very simple sample program which shows how to run brunel on data you already have in memory,
	 * using two data sets, one for nodes and one for links
	 */
	public static void main(String[] args) throws Exception {

		DataCache.useCache(cache);

		Field nodeID = Fields.makeColumnField("ID", "ID", new String[]{
				"a", "b", "c", "d", "e"
		});

		Field edgeFrom = Fields.makeColumnField("From", "From", new String[]{
				"a", "b", "c", "b", "d", "c"
		});

		Field edgeTo = Fields.makeColumnField("To", "To", new String[]{
				"b", "c", "e", "d", "e", "d"
		});

		String command = "data('edges') key(From, To) + data('nodes') network key(ID)";

		Dataset nodeData = Dataset.make(new Field[]{nodeID});
		Dataset edgeData = Dataset.make(new Field[]{edgeFrom, edgeTo});

		// Define a builder using default options
		BuilderOptions options = new BuilderOptions();
		VisualizationBuilder builder = VisualizationBuilder.make(options);

		// Probably don't need the synchronization, but might adds a little safety
		synchronized (cache) {

			// Store in our cache
			cache.store("nodes", nodeData);
			cache.store("edges", edgeData);

			// Build the action and apply it to create the VisItem
			Action action = Action.parse(command);
			VisItem vis = action.apply();

			cache.remove("nodes");
			cache.remove("edges");

			// Build the visualization into a 600x600 area
			builder.build(vis, 600, 600);

		}

		// output to standard out
		StringWriter writer = new StringWriter();
		new PageOutput(builder, writer).write();
		System.out.println(writer.toString());

	}

}
