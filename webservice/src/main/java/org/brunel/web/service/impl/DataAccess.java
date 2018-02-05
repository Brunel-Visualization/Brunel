package org.brunel.web.service.impl;

import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by graham on 1/31/18.
 */
public class DataAccess {
  public static Dataset readData(String url, boolean errorsFormattedAsHTML) {
    if (url == null) {
      return null;
    }

    try {
      return DataCache.get(url);
    } catch (Exception e) {
      throw ExceptionBuilding.error(
        "Error reading data",
        "Could not read data as CSV from: " + url, errorsFormattedAsHTML, e);
    }
  }

  public static Dataset makeFromCSVString(String csvData, boolean errorsFormattedAsHTML) {
    try {
      return Dataset.make(CSV.read(csvData));
    } catch (Exception e) {
      throw ExceptionBuilding.error(
        "Error reading data",
        "Could not parse data passed in as CSV data", errorsFormattedAsHTML, e);
    }
  }


  public static String readCSV(String url, boolean errorsFormattedAsHTML) {
    try {
      URL website = new URL(url);
      URLConnection connection = website.openConnection();
      BufferedReader in = new BufferedReader(
        new InputStreamReader(
          connection.getInputStream()));

      StringBuilder response = new StringBuilder();
      String inputLine;

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
        response.append("\n");
      }

      in.close();

      return response.toString();
    } catch (Exception e) {
      throw ExceptionBuilding.error(
        "Error reading sample data file: " + url,
        "Could not the sample data", errorsFormattedAsHTML, e);
    }
  }

}
