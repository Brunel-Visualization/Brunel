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

package org.brunel.util;



import org.brunel.action.Action;
import org.brunel.action.ActionUtil;
import org.brunel.action.Param;
import org.brunel.build.VisualizationBuilder;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.brunel.model.VisException;
import org.brunel.model.VisItem;

import com.google.gson.Gson;

/**
 * Brunel integration methods provided for services and other languages.  Only primitives are used for language integration methods
 *
 * Note, these methods currently assume a single dataset.
 *
 */
public class D3Integration {

	private static final Gson gson = new Gson();

	/**
	 * Create and return the Brunel results as a String containing the Brunel JSON.
	 * @param data the data as a CSV String
	 * @param brunelSrc the brunel syntax
	 * @param width the desired width for the visualization
	 * @param height the desired height for the visualization
	 * @param visId an identifier used in the SVG tag that will contain the visualization
	 * @return a String that is JSON containing the Brunel JS, CSS and interactive control metadata.
	 */

	//Note:   This method is called from other languages.
	//Do not modify this method signature without checking all language integrations.
    public static String createBrunelJSON(String data, String brunelSrc, int width,  int height, String visId, String controlsId) {
			try {
				BrunelD3Result result = createBrunelResult(data, brunelSrc, width, height, visId, controlsId);
				return gson.toJson(result) ;
			}
			catch (Exception ex) {
	    		throw new RuntimeException(buildExceptionMessage(ex,ex.getMessage(), ".  "));
	    	}
    }

    /**
     * Store a dataset in the cache with the given key.  The key can then be used in Brunel data() statements to reference that data.
     * @param dataKey a unique key name for the data
     * @param data the dataset
     */
	//Note:   This method is called from other languages.
	//Do not modify this method signature without checking all language integrations.
    public static void cacheData(String dataKey, Dataset data) {
    	DataCache.store(dataKey, data);
    }

    /**
     * Store a dataset provided as CSV in the cache with the given key.  The key can then be used in Brunel data() statements to reference that data.
     * @param dataKey a unique key name for the data
     * @param csv the dataset as a CSV String
     */
	//Note:   This method is called from other languages.
	//Do not modify this method signature without checking all language integrations.
    public static void cacheData(String dataKey, String csv) {
    	DataCache.store(dataKey, makeBrunelData(csv));
    }

    /**
     * Get all dataset names from data() statements that are supplied in the given brunel.
     * @param brunel the brunel syntax
     */
	//Note:   This method is called from other languages.
	//Do not modify this method signature without checking all language integrations.
    public static String[] getDatasetNames(String brunel) {
    	Param[] params = ActionUtil.dataParameters(Action.parse(brunel));
    	String[] names = new String[params.length];
    	for (int i=0; i< names.length; i++) {
    		names[i] = params[i].asString();
    	}
    	return names;
    }

    /**
     * Prefix all data statements with a supplied String.  This is provided to allow unique data set
     * names to be placed into the cache.  This will take the supplied Brunel along with a prefix presumed
     * to be unique for the session and prefix it to all data() statements.
     * @param brunel the original Brunel
     * @param prefix the prefix to use
     * @return new Brunel with all data() statements containing the suppplied prefix.
     */
	//Note:   This method is called from other languages.
	//Do not modify this method signature without checking all language integrations.
    public static String prefixAllDataStatements(String brunel, String prefix) {
    	return ActionUtil.prefixAllDataStatements(Action.parse(brunel), prefix);

    }

	/**
	 * Create and return the Brunel results as a String containing the Brunel JSON.
	 * @param data the data as a CSV String
	 * @param brunelSrc the brunel syntax
	 * @param width the desired width for the visualization
	 * @param height the desired height for the visualization
	 * @param visId an identifier used in the SVG tag that will contain the visualization
	 * @return a Gson serializable object containing the Brunel JS, CSS and interactive control metadata.
	 */

    public static BrunelD3Result createBrunelResult(String data, String brunelSrc, int width,  int height, String visId, String controlsId) {
    			Dataset dataset = makeBrunelData(data);
				VisualizationBuilder builder = makeD3(dataset, brunelSrc, width, height, visId, controlsId);
				BrunelD3Result result = new BrunelD3Result();
				result.css = builder.getStyleOverrides();
				result.js = builder.getVisualization().toString();
				result.controls = builder.getControls();
				return result;
    }


    /**
     * Append Brunel exception messages following the cause of a given exception stack trace, stopping when reaching a VisException.
     * @param thrown the Exception that was thrown.  The message for this exception is not included in the results.
     * @param message An initial message (or a blank string)
     * @param messageSeparator A separator for the individual messages
     * @return the full message
     */

    public static String buildExceptionMessage (Throwable thrown, String message, String messageSeparator) {
    	Throwable cause = thrown.getCause();
    	while (cause != null) {
    		message += messageSeparator + cause.getMessage();
    		if (cause instanceof VisException) break; else cause = cause.getCause();
    	}

    	return message;
    }


	//Creates a D3Builder to produce the d3 output
    public static VisualizationBuilder makeD3(Dataset data, String actionText, int width, int height, String visId, String controlsId) {
    	try {
            BuilderOptions options = BuilderOptions.makeFromENV();
            options.visIdentifier = visId;
            options.controlsIdentifier = controlsId;
            VisualizationBuilder builder = VisualizationBuilder.make(options);
            VisItem item = makeVisItem(data, actionText);
            builder.build(item, width, height);
            return builder;
    	} catch (Exception ex) {
        	ex.printStackTrace();
            throw new IllegalArgumentException("Could not execute Brunel: " + actionText, ex);
        }
    }

    //Create a Dataset instance given CSV
    private static Dataset makeBrunelData(String data) {
    	if (data == null || data.isEmpty()) return null;
    	try {
            return  Dataset.make(CSV.read(data));
    	 } catch (Exception e) {
             throw new IllegalArgumentException("Could not create data as CSV from content", e);
         }

    }


    //Create the VisItem instance for the given Brunel
    private static VisItem makeVisItem(Dataset brunel, String actionText) {
        Action action = Action.parse(actionText);
        if (brunel == null) return action.apply();
        return action.apply(brunel);
    }



}
