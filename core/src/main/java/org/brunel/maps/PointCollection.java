package org.brunel.maps;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Collects points so as to make both bounds and a convex hull
 */
public class PointCollection {

    private final Set<Point> points = new HashSet<Point>();
    private Rect bounds;
    private Point[] hull;

    public void add(double x, double y) {
        points.add(new Point(x,y));
    }

    public Rect bounds() {
        if (bounds == null) build();
        return bounds;
    }

    public Point[]convexHull() {
        if (hull == null) build();
        return hull;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    private void build() {
        makeConvexHull();
        bounds = makeBounds();
    }

    private boolean makeConvexHull() {
        // Get points, sorted by Y and then X
        Point[] points = sortedPoints();

        // Stack to manipulate hull with
        Stack<Point> stack = new Stack<Point>();

        // Sort so bottom left is the first
        Point p0 = points[0];
        stack.push(p0);
        int N = points.length;

        // Sort by polar angle relative to p0
        Arrays.sort(points, 1, N, new PolarComparator(p0));

        // find next different point - p1
        int i = 1;
        while (i < N && p0.equals(points[i])) i++;
        if (i == N) return true;
        Point p1 = points[i];

        // find next point not collinear with p0,p1
        i++;
        while (i < N && Point.ccw(p0, p1, points[i]) == 0) i++;
        stack.push(points[i - 1]);        // This is the second point on the hull

        // The main Graham Scan loop -- keep trying to add a new point to the end
        while (i < N) {
            Point top = stack.pop();
            while (Point.ccw(stack.peek(), top, points[i]) <= 0) top = stack.pop();
            stack.push(top);
            stack.push(points[i]);
            i++;
        }

        this.hull = stack.toArray(new Point[stack.size()]);
        if (!isConvex()) throw new IllegalStateException("NOT CONVEX!");
        return false;
    }

    private Rect makeBounds() {
        Rect bounds = null;
        for (Point p : hull) {
            if (bounds == null) bounds = new Rect(p.x, p.x, p.y, p.y);
            else bounds = bounds.union(p.x, p.y);
        }
        return bounds;
    }

    private Point[] sortedPoints() {
        Point[] pts = points.toArray(new Point[points.size()]);
        Arrays.sort(pts);
        return pts;
    }

    // check that boundary of hull is strictly convex
    private boolean isConvex() {
        int N = hull.length;
        if (N <= 2) return true;
        for (int i = 0; i < N; i++)
            if (Point.ccw(hull[i], hull[(i + 1) % N], hull[(i + 2) % N]) <= 0) {
                return false;
            }
        return true;
    }

    private class PolarComparator implements Comparator<Point> {
        private final Point p;

        public PolarComparator(Point p) {
            this.p = p;
        }

        public int compare(Point q1, Point q2) {
            return -Point.ccw(p, q1, q2);
        }
    }

}
