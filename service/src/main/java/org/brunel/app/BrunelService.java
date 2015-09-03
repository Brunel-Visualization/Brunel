/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.app;

import org.brunel.action.Action;
import org.brunel.build.Builder;
import org.brunel.build.controls.ControlWriter;
import org.brunel.build.controls.Controls;
import org.brunel.build.d3.D3Builder;
import org.brunel.build.util.ContentReader;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.brunel.match.BestMatch;
import org.brunel.model.VisItem;
import org.brunel.util.WebDisplay;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;

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
import javax.ws.rs.core.Response.Status;

import java.io.IOException;
import java.io.InputStream;

/**
 * Sample JAX-RS web application that produces d3 JS/CSS.
 * <p/>
 * REST pattern is:
 * <p/>
 * POST /brunel/interpret/d3?src={brunel}&width=..&height=..
 * <p/>
 * Payload is expected to be CSV as TEXT/PLAIN.
 */

@ApplicationPath("brunel")
@Path("interpret")
public class BrunelService extends Application {

	//TODO:  Will be removed
    @POST
    @Path("data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    //For data uploading.  Note, data stored in memory for the moment.

    public void addSource(@Multipart("file") Attachment attachment, @Multipart("file_name") String fileName) {
        try {
            // The getting part caches it
            InputStream is = attachment.getObject(InputStream.class);
            DataCache.get(fileName, is);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @GET
    @Path("match")
    @Produces(MediaType.TEXT_PLAIN)

    //Get action on new data using previous data and supplied action
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
            // You would have to be really unlucky ot get this -- the cache would have to be flushed and then the
            // the remote fle fail to be read.
            throw makeException("Could not read data for match: " + e.getMessage(), Status.BAD_REQUEST.getStatusCode());
        }
    }

    @POST
    @Path("d3")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)  //JSON object with "js" and "css" entries
    //Note, this call requires an identifier to use for the d3 JS to reference the HTML tag (usually an SVG tag).
    public D3Result createAsD3(String data, @QueryParam("src") String brunelSrc,
                               @QueryParam("width") int width,
                               @QueryParam("height") int height,
                               @QueryParam("visid") String visId) {
        Builder builder = makeD3(makeBrunelData(data), brunelSrc, width, height, visId);
        D3Result result = new D3Result();
        result.css = builder.getStyleOverrides();
        result.js = builder.getVisualization().toString();
        result.controls = builder.getControls();
        return result;
    }

    private Builder makeD3(Dataset data, String actionText, int width, int height, String visId) {

        try {
            D3Builder builder = new D3Builder(visId);
            VisItem item = makeVisItem(data, actionText);
            builder.build(item, width, height);
            return builder;
        } catch (Exception ex) {
            throw makeException("Could not execute Brunel: " + actionText + ": " + ex.getMessage(), Status.BAD_REQUEST.getStatusCode());
        }
    }

    private Dataset makeBrunelData(String data) {
        try {
            return  Dataset.make(CSV.read(data));
        } catch (Exception e) {
            throw makeException("Could not create data as CSV from content", Status.BAD_REQUEST.getStatusCode());
        }
    }


    private Dataset readBrunelData(String url) {
        try {
            return DataCache.get(url);
        } catch (Exception e) {
            throw makeException("Could not read data as CSV from: " + url, Status.BAD_REQUEST.getStatusCode());
        }
    }

    private VisItem makeVisItem(Dataset brunel, String actionText) {
        Action action = Action.parse(actionText);
        return action.apply(brunel);
    }

    private WebApplicationException makeException(String message, int code) {

        return new WebApplicationException(
                Response.status(Status.fromStatusCode(code))
                        .entity(message).type(MediaType.TEXT_PLAIN).build());
    }

    @GET
    @Path("d3")
    @Produces(MediaType.TEXT_HTML)


    public String createAsD3Html(@QueryParam("brunel_src") String brunelSrc,
    							 @QueryParam("brunel_url") String brunelUrl,
                                 @QueryParam("width") int width,
                                 @QueryParam("height") int height,
                                 @QueryParam("data") String dataUrl,
                                 @QueryParam("files") String filesLoc
    ) {

    	try {
	    	String src = brunelSrc != null ? brunelSrc : ContentReader.readContentFromUrl(brunelUrl);

	        Builder builder = makeD3(readBrunelData(dataUrl), src, width, height, "visualization");
	        String css = builder.getStyleOverrides();
	        String js = (String) builder.getVisualization();
	        Controls controls = builder.getControls();
	        js += new ControlWriter("controls", "BrunelJQueryControlFactory").write(controls);
	        if (filesLoc == null) filesLoc = "../../brunelsupport";
	        if (width < 5) width = 800;
	        if (height < 5) height = 600;
	        return WebDisplay.writeHtml(css, js, width, height, filesLoc, null, controls, "");
    	}
    	catch (IOException ex) {
    		 throw makeException("Could not read brunel from: " + brunelUrl, Status.BAD_REQUEST.getStatusCode());
    	}

    }

}
