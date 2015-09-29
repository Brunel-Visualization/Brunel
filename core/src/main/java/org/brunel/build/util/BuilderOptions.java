package org.brunel.build.util;

/**
 * Options that can be set for a builder
 */
public class BuilderOptions {


    /**
     * none -  no data described
     * full - send full data set
     * columns - send only required columns
     * minimal - send the minimal data needed by the system
     */
    public enum DataMethod { none, full, columns, minimal}

    public String visIdentifier = "visualization";              // The HTML ID of the SVG element containign the vis
    public String dataName = "table%d";                         // Pattern for the data table ID. %d is the index.
    public String className = "BrunelVis";                      // Name of the base function
    public DataMethod includeData = DataMethod.columns;         // What level of data to include
    public boolean exposeHooks = true;                          // if true, expose hooks for Javascript to use
    public boolean generateBuildCode = true;                    // if true, Add javascript to build the chart initially
    public boolean readableJavascript = true;                   // Readable or shorter
    public String localResources;                               // If set, get resources from this local directory
    public String version = "0.7";                              // Which online version to use

}
