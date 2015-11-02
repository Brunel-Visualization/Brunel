package org.brunel.maps;

/**
 * Created by graham on 10/15/15.
 */
public abstract class Projection {

    public static void main(String[] args) {
        Projection a = new Mercator();
        Projection b = new Albers(22, 66, 34);
        for (int i = 1; i < 60; i++)
            for (int j = 1; j < 60; j++) {
                double lon = i * 6 - 180;
                double lat = i * 3 - 90;
                testTransform(a, lon, lat);
                testTransform(b, lon, lat);
            }
    }

    private static void testTransform(Projection a, double lon, double lat) {
        double[] pa = a.transform(lon, lat);
        double[] ia = a.inverse(pa[0], pa[1]);
        double da = Math.max(Math.abs(lon - ia[0]), Math.abs(lat - ia[1]));
        if (da > 0.0001) {
            throw new IllegalStateException("Bad transforms for " + a.getClass().getSimpleName() + " at " + lon + ", " + lat);
        }
    }

    /**
     * Projects forward
     *
     * @param lon degrees longitude
     * @param lat degrees latitude
     * @return 2D screen coordinates
     */
    public abstract double[] transform(double lon, double lat);

    /**
     * Projects backwards
     *
     * @param x screen coordinates
     * @param y screen coordinates
     * @return 2D longitude, latitude
     */
    public abstract double[] inverse(double x, double y);

    private static double asRadians(double lon) {
        return Math.toRadians(lon);
    }

    /**
     * Rough estimate of the area of a small rectangle at the given location, when projected
     */
    public double getTissotArea(double lon, double lat) {
        double h = 5e-4;            // About 50m at the equator
        double dx = distanceX(lon - h, lon + h, lat);
        double dy = distanceY(lat - h, lat + h, lon);
        return dx * dy;
    }

    /**
     * Screen distance between points when projected
     *
     * @param lon1 degrees
     * @param lon2 degrees
     * @param lat  degrees, common for both latitudes
     * @return distance in screen units, >= 0
     */
    public final double distanceX(double lon1, double lon2, double lat) {
        double[] p = transform(lon1, lat);
        double[] q = transform(lon2, lat);
        return Math.abs(p[0] - q[0]);
    }

    /**
     * Screen distance between points when projected
     *
     * @param lat1 degrees
     * @param lat2 degrees
     * @param lon  degrees, common for both latitudes
     * @return distance in screen units, >= 0
     */
    public final double distanceY(double lat1, double lat2, double lon) {
        double[] p = transform(lon, lat1);
        double[] q = transform(lon, lat2);
        return Math.abs(p[1] - q[1]);
    }

    public double[] maxExtents(double x1, double x2, double y1, double y2) {
        double xm = (x1 + x2) / 2;
        double ym = (y1 + y2) / 2;
        double[][] pts = new double[][]{
                {x1, y1}, {xm, y1}, {x2, y1}, {x2, ym},
                {x2, y2}, {xm, y2}, {x1, y2}, {x1, ym},
        };

        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (int i = 0; i < pts.length; i++) {
            double[] p = transform(pts[i][0], pts[i][1]);
            if (i == 0) {
                minX = maxX = p[0];
                minY = maxY = p[1];
            } else {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
        }
        return new double[]{minX, maxX, minY, maxY};
    }

    public final static class Mercator extends Projection {
        public double[] transform(double lon, double lat) {
            double a = asRadians(lon);
            double b = asRadians(lat);
            return new double[]{a, Math.log(Math.tan(Math.PI / 4 + b / 2))};
        }

        public double[] inverse(double x, double y) {
            double a = Math.toDegrees(x);
            double b = Math.toDegrees(2 * Math.atan(Math.exp(y)) - Math.PI / 2);
            return new double[]{a, b};
        }

    }

    public final static class WinkelTripel extends Projection {
        public double[] transform(double lon, double lat) {
            double x = asRadians(lon);
            double y = asRadians(lat);

            double a = Math.acos(Math.cos(y) * Math.cos(x / 2));
            double sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;

            return new double[]{Math.cos(y) * Math.sin(x / 2) / sinca + x / Math.PI,
                    (Math.sin(y) * sinca + y) / 2};
        }

        public double[] inverse(double x, double y) {
            throw new UnsupportedOperationException("Inverse not available for Winkel Triple");
        }

    }

    public final static class Albers extends Projection {

        private final double sin1, sin2, n;     // sins at parallels and their midpoint
        private final double C, r1;             // Other transform constants
        private final double rotation;          // Rotation for longitude

        public Albers(double parallelA, double parallelB, double rotation) {
            this.rotation = rotation;

            double s1 = asRadians(parallelA);
            double s2 = asRadians(parallelB);

            sin1 = Math.sin(s1);
            sin2 = Math.sin(s2);

            n = (sin1 + sin2) / 2;
            C = 1 + sin1 * (2 * n - sin1);
            r1 = Math.sqrt(C) / n;

        }

        public double[] transform(double lon, double lat) {
            double a = asRadians(lon + rotation);
            double b = asRadians(lat);
            double r = Math.sqrt(C - 2 * n * Math.sin(b)) / n;
            return new double[]{
                    r * Math.sin(a * n),
                    r1 - r * Math.cos(a * n)
            };
        }

        public double[] inverse(double x, double y) {
            double r1y = r1 - y;
            double lon = Math.atan2(x, r1y) / n;
            double v = (C - (x * x + r1y * r1y) * n * n) / (2 * n);
            double lat = Math.asin(Math.min(1, Math.max(-1, v)));
            return new double[]{
                    Math.toDegrees(lon) - rotation,
                    Math.toDegrees(lat)
            };
        }

    }

}
