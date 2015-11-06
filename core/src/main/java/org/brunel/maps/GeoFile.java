package org.brunel.maps;

/**
 * Keeps basic information on a geo file
 */
class GeoFile implements Comparable<GeoFile> {
    public final String name;           // File name
    public final int index;             // index within the global list of files
    public final double size;           // Size in Kbytes
    public final Rect bounds;           // longitude min, max; latitude min,max
    public final double[][] hull;       // Convex hull in lat/long

    public GeoFile(String name, int index, String boundsString, String hullString, String sizeString) {
        this.name = name;
        this.index = index;
        this.size = Integer.parseInt(sizeString);
        String[] b = boundsString.split(",");
        this.bounds = new Rect(Double.parseDouble(b[0]), Double.parseDouble(b[1]), Double.parseDouble(b[2]), Double.parseDouble(b[3]));
        this.hull = parse(hullString);
    }

    private double[][] parse(String hullString) {
        String[] parts = hullString.split(";");
        double[][] result = new double[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] p = parts[i].split(",");
            result[i] = new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])};
        }
        return result;
    }

    public int compareTo(GeoFile o) {
        return name.compareTo(o.name);
    }

    public boolean covers(double x, double y) {
        boolean inBounds =  bounds.contains(x, y);
        boolean inHull =  hullContains(x, y);
        if (inHull && !inBounds) throw new IllegalStateException("Hull error");
        return inHull;
    }

    public String toString() {
        return name;
    }


    private boolean hullContains(double px, double py) {
        // This method uses the ray casting algorithm described at
        // http://en.wikipedia.org/wiki/Point_in_polygon


        // It checks intersections of horizontal ray through y with the sides of the polygon
        // If there is an odd number of intersections to one side, the point is inside
        int intersectCountLeft = 0;

        // Go through each combination of coordinates
        // j will always point to the position before i
        int j = hull.length - 1;


        // horizontal line: y = y
        for (int i = 0; i < hull.length; i++) {
            // Determine line formula for current segment: y = mx + b
            double yj = hull[j][1];
            double yi = hull[i][1];
            double xj = hull[j][0];
            double xi = hull[i][0];
            double m = (yj - yi) / (xj - xi);

            if (Double.isNaN(m)) {
                // Both points are identical. Ignore this segment
            } else if (Math.abs(m) < 1e-6) {
                // two horizontal lines won't intersect (and if they do we don't want to increment anyway)
            } else if (Math.abs(m) > 1e6) {
                // Vertical line.
                double intersectX = xi;
                double intersectY = py;

                if ((intersectY > yi - 1e-3 && intersectY < yj + 1e-3)
                        || (intersectY > yj - 1e-3 && intersectY < yi + 1e-3)) {
                    // Is the intersection to the left or right?
                    if (intersectX < px) {
                        // If the horizontal ray hits a vertex, we have a
                        // special case.
                        if (Math.abs(intersectY - yi) < 1e-3) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            if (yj > py) {
                                intersectCountLeft++;
                            }
                        } else if (Math.abs(intersectY - yj) <= 1e-3) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            if (yi > py) {
                                intersectCountLeft++;
                            }
                        } else {
                            intersectCountLeft++;
                        }
                    }
                }
            } else {
                // Solve for b: y - mx, using first point
                double b = yi - m * xi;

                // Find the x position of the intersection point of the segment
                // with the horizontal ray.
                // x = (y - b)/m
                double intersectX = (py - b) / m;

                if ((intersectX > xi - 1e-3 && intersectX < xj + 1e-3)
                        || (intersectX > xj - 1e-3 && intersectX < xi + 1e-3)) {

                    // Is the intersection to the left or right?
                    if (intersectX < px) {
                        // If the horizontal ray hits a vertex, we have a
                        // special case.
                        if (Math.abs(py - yi) <= 1e-3) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            // Screen coordinates - below is greater than.
                            if (yj > py) {
                                intersectCountLeft++;
                            }
                        } else if (Math.abs(py - yj) <= 0) {
                            // Only count if the other vertex on this segment is
                            // below the position.
                            if (yi > py) {
                                intersectCountLeft++;
                            }
                        } else {
                            intersectCountLeft++;
                        }
                    }
                }
            }
            j = i;
        }

        return intersectCountLeft % 2 != 0;


    }
}
