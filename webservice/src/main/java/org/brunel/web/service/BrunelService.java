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

package org.brunel.web.service;

import com.google.gson.Gson;
import org.brunel.build.VisualizationBuilder;
import org.brunel.build.util.ContentReader;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.util.D3Integration;
import org.brunel.util.WebDisplay;

import javax.ws.rs.GET;
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
 * Sample JAX-RS web application that produces Brunel visualizations.
 * Payload is expected to be CSV as TEXT/PLAIN.
 */

@Path("/")
public class BrunelService extends Application {

	private static final Gson gson = new Gson();

	private static final String ERROR_TEMPLATE = "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css'>\n" +
			"<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap-theme.min.css'>\n" +
			"<script src='//ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js' charset='utf-8'></script>\n" +
			"<script src='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js'></script>\n" +
			"<div class='alert alert-danger'>\n" +
			"<strong>Error!</strong> %s\n" +
			"</div>";


	/**
	 * Creates a full HTML page suitable for use within an HTML IFrame.
	 *
	 * @param brunelSrc   the Brunel syntax defining the visualization
	 * @param brunelUrl   (optional) a URL to a file containing the Brunel syntax
	 * @param showBrunel  if true, show the brunel command
	 * @param width       the desired width of the resulting visualization
	 * @param height      the desired height of the resulting visualization
	 * @param title       (optional) title to include with the visualization
	 * @param description (optional) description to include with the visualization
	 * @param dataUrl     a URL pointing to the CSV to use for the visualization's data.  Note if the Brunel contains a data()
	 *                    function, then this will be used instead
	 * @param filesLoc    (optional) an alternate location for the main Brunel javascript
	 * @param prefix      (optional) The prefix used to uniquely identify data for a given user session when adding data to the cache.
	 * @return a full HTML page with all JS/CSS and interactive controls for a given visualization.
	 */
	@GET()
  @Path("/html")
	@Produces(MediaType.TEXT_HTML)
	public Response createAsD3Html(@QueryParam("brunel_src") String brunelSrc,
								   @QueryParam("brunel_url") String brunelUrl,
								   @QueryParam("width") int width,
								   @QueryParam("height") int height,
								   @QueryParam("title") String title,
								   @QueryParam("description") String description,
								   @QueryParam("show_brunel") String showBrunel,
								   @QueryParam("data") String dataUrl,
								   @QueryParam("files") String filesLoc,
								   @QueryParam("data_prefix") String prefix
	) {

		try {

			if (title == null) title = "";
			if (description == null) description = "";
			String brunelStr = new Boolean(showBrunel) ? brunelSrc : "";
			if (prefix != null && brunelSrc != null)
				brunelSrc = D3Integration.prefixAllDataStatements(brunelSrc, prefix);

			String[] titles = new String[]{title, description};
			String src = brunelSrc != null ? brunelSrc : ContentReader.readContentFromUrl(URI.create(brunelUrl));
			VisualizationBuilder builder = D3Integration.makeD3(readBrunelData(dataUrl, true), src, width, height, "visualization", "controls");
			String response = WebDisplay.writeHtml(builder, width, height, brunelStr, titles);
			return Response.ok(response).header("Access-Control-Allow-Origin", "*").build();
		} catch (IOException ex) {
			throw makeException("Could not read brunel from: " + brunelUrl, ex, Status.BAD_REQUEST.getStatusCode(), true);
		} catch (Exception ex) {
			throw makeException(ex.getMessage(), ex, Status.BAD_REQUEST.getStatusCode(), true);

		}

	}

	//Get a Dataset instance given a URL.  The content will be loaded if not present in the cache.
	private Dataset readBrunelData(String url, boolean formattedError) {
		try {
			return DataCache.get(url);
		} catch (Exception e) {
			throw makeException("Could not read data as CSV from: " + url, e, Status.BAD_REQUEST.getStatusCode(), formattedError);
		}
	}

	//Simple web exception handling.  A bootstrap HTML formatted message is returned for <iframe> requests.
	private WebApplicationException makeException(String message, Exception thrown, int code, boolean formatted) {

		String separator = formatted ? "<P><P>" : "\n";
		message += D3Integration.buildExceptionMessage(thrown, message, separator);

		String t = MediaType.TEXT_PLAIN;
		if (formatted) {
			t = MediaType.TEXT_HTML;
			message = String.format(ERROR_TEMPLATE, message);
		}

		ResponseBuilder rb = Response.status(Status.fromStatusCode(code)).header("Access-Control-Allow-Origin", "*").
				entity(message).type(t);

		return new WebApplicationException(rb.build());
	}

}
