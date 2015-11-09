package org.brunel.maps;

/**
 * A simple point
 */
public class Point implements Comparable<Point> {

    public static int ccw(Point a, Point b, Point c) {
        double area2 = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        return area2 < 0 ? -1 : (area2 > 0 ? 1 : 0);
    }

    public final double x, y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public int compareTo(Point o) {
        Point p = (Point) o;
        int d = Double.compare(y, p.y);
        return d == 0 ? Double.compare(x, p.x) : d;
    }

    public String toString() {
        return "(" + f(x) + "," + f(y) + ")";
    }

    public Point toWinkelTripel() {
        double lambda = Math.toRadians(x);
        double phi = Math.toRadians(y);
        double a = Math.acos(Math.cos(phi) * Math.cos(lambda / 2));
        double sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;
        return new Point(Math.cos(phi) * Math.sin(lambda / 2) / sinca + lambda / Math.PI, (Math.sin(phi) * sinca + phi) / 2);
    }

    private double f(double y) {
        return ((int) (y * 100)) / 100.0;
    }

    public int hashCode() {
        int result;
        long temp = Double.doubleToLongBits(x);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        return 31 * result + (int) (temp ^ (temp >>> 32));
    }

    // Unsafe -- only works for non-null Points
    public boolean equals(Object o) {
        return dist2((Point) o) == 0;
    }

    public double dist2(Point o) {
        return (o.x - x) * (o.x - x) + (o.y - y) * (o.y - y);
    }
}
