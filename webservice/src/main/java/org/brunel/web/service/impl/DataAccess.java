package org.brunel.web.service.impl;

import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;

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

}
