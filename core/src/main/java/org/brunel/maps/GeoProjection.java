package org.brunel.maps;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Allows definitions of suitable geo projections
 */
public class GeoProjection {

    public static final String[] WinkelD3Function = new String[]{
            "function () {",
            "  function w(x, y) {",
            "    var a = Math.acos(Math.cos(y) * Math.cos(x / 2)), sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;",
            "    return [Math.cos(y) * Math.sin(x / 2) / sinca + x / Math.PI, (Math.sin(y) * sinca + y) / 2];",
            "  }",
            "  return d3.geo.projection(w);",
            "}"
    };

    public static final Projection MERCATOR = new Projection.Mercator();
    public static final Projection WINKEL3 = new Projection.WinkelTripel();
    public static final String LN = "\n\t\t";
    private final String width;
    private final String height;
    private final String winkelTripleFunctionName;
    private NumberFormat F = new DecimalFormat("#.####");

    /**
     * GeoProjection chooses good projections for D3 to use for a amp
     *
     * @param widthName  name of a Javascript variable that will contain the display width
     * @param heightName name of a Javascript variable that will contain the display height
     */
    public GeoProjection(String widthName, String heightName, String winkelTripleFunctionName) {
        this.width = widthName;
        this.height = heightName;
        this.winkelTripleFunctionName = winkelTripleFunctionName;
    }

    public String makeProjection(Rect bounds) {

        // Are we USA only?
        if (bounds.x1 < -100 && bounds.y1 > 17 && bounds.y2 > 35 && bounds.y2 < 73) {
            // If we need Alaska and/or Hawaii
            if (bounds.x1 < -120) return makeAlbersUSA();
            else return makeMercator(bounds);

        }
        double distortion = getDistortion(bounds);

        // Mercator if the distortion is tolerable
        if (distortion <= 1.8) return makeMercator(bounds);

        // If we cover a wide area, use winkel triple
        if (bounds.x2 - bounds.x1 > 180 && bounds.y2 - bounds.y1 > 90) return makeWinkelTripel(bounds);

        // Otherwise albers is our best bet
        return makeAlbers(bounds);

    }

    private String makeAlbersUSA() {
        // The center in screen coords
        String translateToCenter = ".translate([" + width + "/2, " + height + "/2])";
        String scale = ".scale(Math.min(" + width + "/0.96, " + height + "/0.48))";

        return "d3.geo.albersUsa()"
                + LN + scale
                + LN + translateToCenter;
    }

    private String makeMercator(Rect b) {
        double[] p1 = MERCATOR.transform(b.x1, b.y1);
        double[] p2 = MERCATOR.transform(b.x2, b.y2);

        double wd = Math.abs(p1[0] - p2[0]);
        double ht = Math.abs(p1[1] - p2[1]);
        String scale = makeScaleForProjectedDimensions(wd, ht);

        // The center in screen coords
        String translateToCenter = ".translate([" + width + "/2, " + height + "/2])";

        // We find the center in projected space, and then invert the projection
        double[] c = MERCATOR.inverse((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2);
        String center = ".center([" + F.format(c[0]) + ", " + F.format(c[1]) + "])";

        return "d3.geo.mercator()"
                + LN + translateToCenter
                + LN + scale
                + LN + center;
    }

    private double getDistortion(Rect bounds) {
        if (bounds.y1 < -89 || bounds.y2 > 89) return 100;
        double a = MERCATOR.getTissotArea(bounds.cx(), bounds.y1);
        double b = MERCATOR.getTissotArea(bounds.cx(), bounds.y2);
        return a < b / 100 || b < a / 100 ? 100 : Math.max(b / a, a / b);
    }

    private String makeWinkelTripel(Rect bounds) {

        Rect ext = WINKEL3.maxExtents(bounds);

        String scale = makeScaleForProjectedDimensions(ext.width(), ext.height());

        // The center in screen coords
        String translateToCenter = ".translate([" + width + "/2, " + height + "/2])";

        // Finding the center is tricky because we cannot invert the transform
        // so we have to search for it
        double y = 0, dy = 9e99;
        for (int i = -90; i < 90; i++) {
            double[] p = WINKEL3.transform(bounds.cx(), i);
            double dp = Math.abs(p[1] - ext.cy());
            if (dp < dy) {
                dy = dp;
                y = i;
            }
        }

        double x = 0, dx = 9e99;
        for (int i = -180; i < 180; i++) {
            double[] p = WINKEL3.transform(i, y);
            double dp = Math.abs(p[0] - ext.cx());
            if (dp < dx) {
                dx = dp;
                x = i;
            }
        }

        String center = ".center([" + F.format(x) + ", " + F.format(y) + "])";
        return winkelTripleFunctionName + "()"
                + LN + translateToCenter
                + LN + scale
                + LN + center;
    }

    private String makeAlbers(Rect b) {

        // Parallels at 1/6 and 5/6 of the latitude
        double parallelA = (b.y1 + b.y2 * 5) / 6;           // Parallels at 1/6 and 5/6
        double parallelB = (b.y1 * 5 + b.y2) / 6;           // Parallels at 1/6 and 5/6
        double angle = -b.cx();                             // Rotation angle

        Projection projection = new Projection.Albers(parallelA, parallelB, angle);

        String parallels = ".parallels([" + F.format(parallelA) + "," + F.format(parallelB) + "])";
        String rotate = ".rotate([" + F.format(angle) + ",0,0])";

        Rect ext = projection.maxExtents(b);

        String scale = makeScaleForProjectedDimensions(ext.width(), ext.height());

        // The center in screen coords
        String translateToCenter = ".translate([" + width + "/2, " + height + "/2])";

        // We find the center in projected space, and then invert the projection
        double[] c = projection.inverse(ext.cx(), ext.cy());
        String center = ".center([0, " + F.format(c[1]) + "])";

        return "d3.geo.albers()"
                + LN + translateToCenter
                + LN + center
                + LN + parallels
                + LN + scale
                + LN + rotate;
    }

    private String makeScaleForProjectedDimensions(double wid, double ht) {
        return ".scale(Math.min((" + width + "-4)/" + F.format(wid) + ", (" + height + "-4)/" + F.format(ht) + "))";
    }
}
