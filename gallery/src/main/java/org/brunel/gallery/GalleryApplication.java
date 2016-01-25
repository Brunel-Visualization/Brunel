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
package org.brunel.gallery;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ContentReader;
import org.brunel.build.util.DataCache;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.brunel.util.Library;

import com.ibm.json.java.JSONObject;

/**
 * A simple gallery web application for Brunel. This app is expected to be
 * deployed on IBM Bluemix It requires the brunel service project (as a .jar) so
 * those methods are also exposed. This adds dataset caching an upload feature
 * and a service to generate HTML pages containing the live visualizations given
 * initial Brunel.
 */

@ApplicationPath("gallery_app")
@Path("renderer")
public class GalleryApplication extends Application {

	private static final String INDEX_LOCATION = "/org/brunel/gallery/RenderTemplate.html";
	private static final GalleryCache GALLERY_CACHE = new GalleryCache();
	private static final BuilderOptions OPTIONS = new BuilderOptions();
	private static final String HTML = new Scanner(
			GalleryApplication.class.getResourceAsStream(INDEX_LOCATION),
			"UTF-8").useDelimiter("\\A").next();
		
	static {
		DataCache.useCache(GALLERY_CACHE); // Use the Bluemix Data Cache service
											// when Brunel stores/retrieves data
	}

	/**
	 * Data upload service.  Data is expected to be CSV.
	 * @param file the file to upload (CSV)
	 * @param fileName the name of the file (not currently used)
	 * @return an identifier to locate the uploaded data in the cache.  This can be used in a Brunel data() function.
	 */
	@POST
	@Path("upload_data")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.TEXT_PLAIN)
	public String addSource(@FormParam("file") File file,
			@FormParam("file_name") String fileName) {
		try {
			FileInputStream fis = new FileInputStream(file);
			String content = ContentReader.readContent(fis);
			Dataset dataset = Dataset.make(CSV.read(content));
			fis.close();
			String uuid = UUID.randomUUID().toString();
			GALLERY_CACHE.store(uuid, dataset);
			return uuid;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	

	/**
	 * Create the gallery HTML page given Brunel syntax for a visualization
	 * @param brunelSrc the Brunel syntax
	 * @param (optional) title a title to display
	 * @param (optional) description a description to display
	 * @param (optional) width the desired width of the visualization
	 * @param (optional) height the desired height of the visualization
	 * @return the HTML page displaying the visualization.
	 */
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String render(@QueryParam("brunel_src") String brunelSrc,
			@QueryParam("title") String title,
			@QueryParam("description") String description,
			@QueryParam("width") String width,
			@QueryParam("height") String height,
			@QueryParam("control_height") String controlHeight
			) {

		
		title = title != null ? title : "";
		description = description != null ? description : "";
		width = width != null ? width : "800";
		height = height != null ? height : "450";
		controlHeight = controlHeight != null ? controlHeight : "0";
		brunelSrc = brunelSrc != null ? brunelSrc : "data('sample:US States.csv') bubble label(abbr) size(population) color(dem_rep:reds-blues)";
		
		String htmlVersion =HTML;
		
		String html = htmlVersion.replace("$TITLE$", title);
		html = html.replace("$BRUNEL_SRC$", Data.quote(brunelSrc));
		html = html.replace("$DESCRIPTION$", description);
		html = html.replace("$WIDTH$", width);
		html = html.replace("$HEIGHT$", height);
		html = html.replace("$CONTROL_SIZE$", controlHeight);
		html = html.replace("$VERSION$", OPTIONS.version);
		return html;
	}
	
	@GET
	@Path("univariates")
	@Produces(MediaType.APPLICATION_JSON)
	public JSONObject getBrunelUnivariates(@QueryParam("id") String dataId) {
		Dataset d = null;
		try {
			d = DataCache.get(dataId);
		} catch (IOException e) {
			e.printStackTrace();
		}
		JSONObject results = new JSONObject();
		if (d != null) {
			for (Field f : d.fields) {
				if (!f.isSynthetic()) results.put(f.label, Library.choose(f).toString());
			}			
		}
		return results;
	}
	
	@GET
	@Path("gallery")
	@Produces(MediaType.APPLICATION_JSON)
	public JSONObject getGalleryJson() {
		return ExampleBuilder.GALLERY;
	}
	
	@GET
	@Path("cookbook")
	@Produces(MediaType.APPLICATION_JSON)
	public JSONObject getCookBookJson() {
		return ExampleBuilder.COOKBOOK;
	}

}
