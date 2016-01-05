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
import org.brunel.build.util.ContentReader;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.match.BestMatch;
import org.brunel.util.BrunelD3Result;
import org.brunel.util.D3Integration;
import org.brunel.util.WebDisplay;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.URI;

/**
 * Sample JAX-RS web application that produces Brunel visualizations.  Currently only d3 output is supported.
 * Service methods are provided to create the raw Brunel content (JS/CSS) or a full HTML page.  An additional service
 * method can generate Brunel that shows a given visualization on new data.
 *
 * REST pattern for raw content is:
 *
 * POST /brunel/interpret/d3?src={brunel}&amp;width=..&amp;height=..
 *
 * Payload is expected to be CSV as TEXT/PLAIN.
 */

@ApplicationPath("brunel")
@Path("interpret")
public class BrunelService extends Application {

	private static final String ERROR_TEMPLATE = "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css'>\n" +
			"<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap-theme.min.css'>\n" +
			"<script src='//ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js' charset='utf-8'></script>\n" +
			"<script src='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js'></script>\n" +
			"<div class='alert alert-danger'>\n"+
			"<strong>Error!</strong> %s\n" +
			"</div>";

    /**
     * Generates all JS/CSS using D3 to produce a visualization.  The data can be on the payload or it can be specified using
     * the Brunel data() function.
     * @param data   the data to use for the visualization (as URL or cache identifier)
     * @param brunelSrc the Brunel syntax defining the visualization
     * @param width the desired width of the resulting visualization
     * @param height the desired height of the resulting visualization
     * @param visId an identifier to use for the d3 JS to reference the HTML tag containing the visualization on the web page (usually an SVG tag).
     * @param controlsId an identifier to use for HTML tag that will contain the interactive controls.
     *          If null, then resulting JS will not contain code for the vis controls and the client is responsible for creating any UIs for vis controls using the returned JSON.
     * @return a JSON object containing the css, js, and an object describing interactive controls that require a separate UI
     */
    @POST
    @Path("d3")
    @Consumes(MediaType.TEXT_PLAIN)        //A CSV file is the payload
    @Produces(MediaType.APPLICATION_JSON)  //JSON object with "js" and "css" entries
    public Response createAsD3(String data, @QueryParam("src") String brunelSrc,
                               @QueryParam("width") int width,
                               @QueryParam("height") int height,
                               @QueryParam("visid") String visId,
                               @QueryParam("controlsid") String controlsId
                               ) {

    	try {
    		BrunelD3Result result = D3Integration.createBrunelResult(data, brunelSrc, width, height, visId, controlsId);
    		return Response.ok(result).header("Access-Control-Allow-Origin", "*").build();

    	}
    	catch (Exception ex) {
    		ex.printStackTrace();
    		throw makeException(ex.getMessage(),Status.BAD_REQUEST.getStatusCode(), false);
    	}
    }

    /**
     * Creates a full HTML page suitable for use within an HTML IFrame.
     * @param brunelSrc the Brunel syntax defining the visualization
     * @param brunelUrl (optional) a URL to a file containing the Brunel syntax
     * @param width the desired width of the resulting visualization
     * @param height the desired height of the resulting visualization
     * @param title (optional) title to include with the visualization
     * @param description (optional) description to include with the visualization
     * @param dataUrl a URL pointing to the CSV to use for the visualization's data.  Note if the Brunel contains a data()
     *  function, then this will be used instead
     * @param filesLoc (optional) an alternate location for the main Brunel javascript
     * @return a full HTML page with all JS/CSS and interactive controls for a given visualization.
     */
    @GET
    @Path("d3")
    @Produces(MediaType.TEXT_HTML)
    public Response createAsD3Html(@QueryParam("brunel_src") String brunelSrc,
    							 @QueryParam("brunel_url") String brunelUrl,
                                 @QueryParam("width") int width,
                                 @QueryParam("height") int height,
                                 @QueryParam("title") String title,
                                 @QueryParam("description") String description,
                                 @QueryParam("show_brunel") String showBrunel,
                                 @QueryParam("data") String dataUrl,
                                 @QueryParam("files") String filesLoc
    ) {

    	try {
    		if (title == null) title = "";
    		if (description == null) description = "";
    		String brunelStr = new Boolean(showBrunel) ? brunelSrc : "";

    		String[] titles = new String[] {title, description};
	    	String src = brunelSrc != null ? brunelSrc : ContentReader.readContentFromUrl(URI.create(brunelUrl));
	        D3Builder builder = D3Integration.makeD3(readBrunelData(dataUrl, true), src, width, height, "visualization", "controls");
	        String response = WebDisplay.writeHtml(builder, width, height, null, brunelStr, titles);
    		return Response.ok(response).header("Access-Control-Allow-Origin", "*").build();
    	}
    	catch (IOException ex) {
    		 throw makeException("Could not read brunel from: " + brunelUrl, Status.BAD_REQUEST.getStatusCode(), true);
    	}
    	catch (Exception ex) {
   		 	 throw makeException(ex.getMessage(), Status.BAD_REQUEST.getStatusCode(), true);

    	}

    }


	/**
	 * Service that creates new Brunel syntax to use a given visualization with new data.
	 * @param originalData the original data (as URL or cache identifier)
	 * @param newData the new data (as URL or cache identifier)
	 * @param brunelSrc the Brunel syntax that produced the original visualization
	 * @return Brunel syntax using the new data
	 */
    @GET
    @Path("match")
    @Produces(MediaType.TEXT_PLAIN)

    public String actionFromExisting(@QueryParam("original_data") String originalData,
                                     @QueryParam("new_data") String newData,
                                     @QueryParam("src") String brunelSrc) {
        try {
        	if (originalData != null) {
	            Dataset origDS = DataCache.get(originalData);
	            Dataset newDS = DataCache.get(newData);
	            return BestMatch.match(origDS, newDS, Action.parse(brunelSrc)).toString();
        	}
        	else {
        		return BestMatch.match(brunelSrc, newData).toString();
        	}
        } catch (IOException e) {
            // You would have to be really unlucky to get this -- the cache would have to be flushed and then the
            // the remote file fail to be read.
            throw makeException("Could not read data for match: " + e.getMessage(), Status.BAD_REQUEST.getStatusCode(), false);
        }

        catch (Exception e) {
        	e.printStackTrace();
            throw makeException("Error matching to new data: " + e.getMessage(), Status.BAD_REQUEST.getStatusCode(), false);

        }
    }


    //Get a Dataset instance given a URL.  The content will be loaded if not present in the cache.
    private Dataset readBrunelData(String url, boolean formattedError) {
        try {
            return DataCache.get(url);
        } catch (Exception e) {
            throw makeException("Could not read data as CSV from: " + url, Status.BAD_REQUEST.getStatusCode(), formattedError);
        }
    }


    //Simple web exception handling.  A bootstrap HTML formatted message is returned for <iframe> requests.
    private WebApplicationException makeException(String message, int code, boolean formatted) {

    	String t = MediaType.TEXT_PLAIN;
    	if (formatted) {
    		t = MediaType.TEXT_HTML;
    		message = String.format(ERROR_TEMPLATE, message);
    	}

    	ResponseBuilder rb = Response.status(Status.fromStatusCode(code)).header("Access-Control-Allow-Origin", "*").
                        entity(message).type(t);;

        return new WebApplicationException(rb.build());
	}


}
