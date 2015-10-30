package org.brunel.maps;

/**
 * Keeps basic information on a geo file
 */
public class GeoFile {
    public final String name;           // File name
    public final double size;           // Size in Kbytes
    public final double[] bounds;       // longitude min, max; latitude min,max

    public GeoFile(String name, String boundsString, String sizeString) {
        this.name = name;
        this.size = Integer.parseInt(sizeString);
        String[] b = boundsString.split(",");
        this.bounds = new double[]{
                Double.parseDouble(b[0]),
                Double.parseDouble(b[1]),
                Double.parseDouble(b[2]),
                Double.parseDouble(b[3])
        };
    }

    public boolean isBetter(GeoFile other) {
        return maxDimension() < other.maxDimension();
    }

    private double maxDimension() {
        return Math.max(bounds[1] - bounds[0], bounds[3] - bounds[2]);
    }

    public String toString() {
        return name;
    }
}
