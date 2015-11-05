package org.brunel.maps;

/**
 * Keeps basic information on a geo file
 */
class GeoFile implements Comparable<GeoFile> {
    public final String name;           // File name
    public final int index;             // index within the global list of files
    public final double size;           // Size in Kbytes
    public final double[] bounds;       // longitude min, max; latitude min,max

    public GeoFile(String name, int index, String boundsString, String sizeString) {
        this.name = name;
        this.index = index;
        this.size = Integer.parseInt(sizeString);
        String[] b = boundsString.split(",");
        this.bounds = new double[]{
                Double.parseDouble(b[0]),
                Double.parseDouble(b[1]),
                Double.parseDouble(b[2]),
                Double.parseDouble(b[3])
        };
    }

    public int compareTo(GeoFile o) {
        return name.compareTo(o.name);
    }

    /**
     * Return the extra space between our bounds and the defined one.
     *
     * @param r space to fit around
     * @return extra space around. If we do not fit, this will be very large
     */
    public double fitExcess(double[] r) {
        return fit(r[0] - bounds[0]) + fit(bounds[1] - r[1]) + fit(r[2] - bounds[2]) + fit(bounds[3] - r[3]);
    }

    private double fit(double v) {
        return v > 0 ? v : -1e6 * v;
    }

    public String toString() {
        return name;
    }
}
