package org.brunel.web.service.impl;

import org.brunel.build.VisualizationBuilder;
import org.brunel.data.Dataset;
import org.brunel.util.D3Integration;
import org.brunel.util.WebDisplay;

import javax.ws.rs.core.Response;

/**
 * Created by graham on 1/31/18.
 */
public class BrunelProcessing {
  public static Response build(Dataset data, String syntax, int width, int height, String title, String description) {
    try {
      VisualizationBuilder builder = D3Integration.makeD3(data, syntax, width, height, "visualization", "controls");
      String[] titles = new String[]{title, description};
      String response = WebDisplay.writeHtml(builder, width, height, null, titles);
      return Response
        .ok(response)
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, PATCH, DELETE")
        .header("Access-Control-Allow-Headers", "X-Requested-With,content-type, Authorization")
        .header("Access-Control-Allow-Credentials", "true")
        .build();
    } catch (Exception ex) {
      throw ExceptionBuilding.error("Error processing the brunel command", "It may require syntax changes", true, ex);
    }
  }

}
