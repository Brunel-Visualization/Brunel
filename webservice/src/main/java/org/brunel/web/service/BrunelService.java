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

import org.brunel.data.Dataset;
import org.brunel.web.service.impl.BrunelProcessing;
import org.brunel.web.service.impl.DataAccess;
import org.brunel.web.service.impl.ExceptionBuilding;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Sample JAX-RS web application that produces Brunel visualizations.
 * Payload is expected to be CSV as TEXT/PLAIN.
 */

@Path("/")
public class BrunelService extends Application {

  /**
   * Creates a full HTML page suitable for use within an HTML IFrame.
   *
   * @param syntax      the Brunel syntax defining the visualization
   * @param width       the desired width of the resulting visualization
   * @param height      the desired height of the resulting visualization
   * @param title       (optional) title to include with the visualization
   * @param description (optional) description to include with the visualization
   * @param dataUrl     a URL pointing to the CSV to use for the visualization's data.  Note if the Brunel contains a data()
   *                    function, then this will be used instead
   * @param filesLoc    (optional) an alternate location for the main Brunel javascript
   * @return a full HTML page with all JS/CSS and interactive controls for a given visualization.
   */
  @GET()
  @Path("/html")
  @Produces(MediaType.TEXT_HTML)
  public Response html_get(@QueryParam("syntax") String syntax,
                           @QueryParam("width") Integer width,
                           @QueryParam("height") Integer height,
                           @QueryParam("title") String title,
                           @QueryParam("description") String description,
                           @QueryParam("data") String dataUrl,
                           @QueryParam("files") String filesLoc
  ) {

    Dataset data = DataAccess.readData(dataUrl, true);
    return makeHTMLResponse(syntax, width, height, title, description, data);

  }

  /**
   * Creates a full HTML page suitable for use within an HTML IFrame. The data can be on the payload or it can be specified using
   * the Brunel data() function.
   *
   * @param syntax      the Brunel syntax defining the visualization
   * @param width       the desired width of the resulting visualization
   * @param height      the desired height of the resulting visualization
   * @param title       (optional) title to include with the visualization
   * @param description (optional) description to include with the visualization
   * @param filesLoc    (optional) an alternate location for the main Brunel javascript
   * @return a full HTML page with all JS/CSS and interactive controls for a given visualization.
   */
  @POST
  @Path("html")
  @Consumes(MediaType.MEDIA_TYPE_WILDCARD)        //A CSV file is the payload
  @Produces(MediaType.TEXT_HTML)
  public Response html_post(String csvData, @QueryParam("syntax") String syntax,
                            @QueryParam("width") Integer width,
                            @QueryParam("height") Integer height,
                            @QueryParam("title") String title,
                            @QueryParam("description") String description,
                            @QueryParam("files") String filesLoc
  ) {

    Dataset data = DataAccess.makeFromCSVString(csvData, true);
    return makeHTMLResponse(syntax, width, height, title, description, data);
  }

  private Response makeHTMLResponse(@QueryParam("syntax") String syntax, @QueryParam("width") Integer width, @QueryParam("height") Integer height, @QueryParam("title") String title, @QueryParam("description") String description, Dataset data) {
    if (syntax == null) {
      throw ExceptionBuilding.error(
        "No 'syntax' parameter was specified in the request",
        "Specify a syntax for the visualization. For example, 'syntax=bubble%20color(#row)'", true, null
      );
    }

    if (width == null) {
      width = 500;
    } else if (width < 20) {
      throw ExceptionBuilding.error(
        "Illegal width parameter",
        "'width' is optional, but if specified, must be 20 or greater", true, null);
    }

    if (height == null) {
      height = 500;
    } else if (height < 20) {
      throw ExceptionBuilding.error(
        "Illegal height parameter",
        "'height' is optional, but if specified, must be 20 or greater", true, null);
    }

    if (title == null) {
      title = "";
    }
    if (description == null) {
      description = "";
    }

    return BrunelProcessing.build(data, syntax, width, height, title, description);
  }

}
