package org.brunel.web.service.impl;

import org.brunel.util.D3Integration;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

/**
 * Created by graham on 1/31/18.
 */
public class ExceptionBuilding {

  private static final String ERROR_HEADER =
    "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css'>\n" +
      "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap-theme.min.css'>\n" +
      "<script src='//ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js' charset='utf-8'></script>\n" +
      "<script src='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js'></script>\n" +
      "<div class='alert alert-danger'>\n";

  public static WebApplicationException error(String message, String notes, boolean formattedAsHTML, Exception ex) {
    String text;
    if (formattedAsHTML) {
      text = ERROR_HEADER + String.format("<p><strong>Error: </strong>%s</p><p>%s</p>", message, notes);
      if (ex != null) {
        text += "<p>Exception:</p><ul>" +
          D3Integration.buildExceptionMessage(ex, message, "<li>")
          + "</ul>";
        text += "</div>";
      }
    } else {
      text = message + "\n" + notes;
      if (ex != null) text += "Exception:" + D3Integration.buildExceptionMessage(ex, message, "\n\t");
    }
    return makeException(text, formattedAsHTML, Status.BAD_REQUEST);
  }

  private static WebApplicationException makeException(String message, boolean formattedAsHTML, Status status) {
    ResponseBuilder rb = Response.status(status)
      .header("Access-Control-Allow-Origin", "*")
      .entity(message)
      .type(formattedAsHTML ? MediaType.TEXT_HTML : MediaType.TEXT_PLAIN);
    return new WebApplicationException(rb.build());
  }

}
