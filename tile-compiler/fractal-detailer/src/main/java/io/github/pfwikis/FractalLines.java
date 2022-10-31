package io.github.pfwikis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.pfwikis.model.Edge;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.model.Point;
import io.github.pfwikis.model.Project;

public class FractalLines {

    public static double MAX_DIST = 100;
    private final static double FRACTAL_STRENGTH = 0.2;
    private final static float FRACTAL_SCALE = 10;
    private final static FastNoiseLite NOISE;

    static {
        NOISE = new FastNoiseLite(7);
        NOISE.SetFrequency(FRACTAL_SCALE);
    }

    public static List<LngLat> interpolate(List<LngLat> points, Set<Edge> innerEdges) {
        ArrayDeque<LngLat> open = new ArrayDeque<>(points);
        List<LngLat> result = new ArrayList<>();
        result.add(open.pop());
        LngLat a = result.get(0);

        while (!open.isEmpty()) {
            LngLat b = open.pop();
            double distance = distance(a, b);

            if (distance > MAX_DIST && !isInnerEdge(a, b, innerEdges)) {
                collectFractally(a, b, (int) Math.ceil(Math.log(distance / MAX_DIST) / Math.log(2)), result);
            }
            a = b;
            result.add(b);
        }
        return result;
    }

    private static boolean isInnerEdge(LngLat a, LngLat b, Set<Edge> innerEdges) {
        return innerEdges.contains(new Edge(a, b)) || (a.lng() == 180.0 && b.lng() == 180.0) || (a.lng() == -180.0 && b.lng() == -180.0);
    }

    private static void collectFractally(LngLat lngLatA, LngLat lngLatB, int iterations, List<LngLat> result) {
        var b = Project.fromLatLngToPoint(lngLatB);
        var a = Project.fromLatLngToPoint(lngLatA);
        collectFractally(a, b, iterations, result);
    }

    private static void collectFractally(Point a, Point b, int iterations, List<LngLat> result) {
        if (iterations == 0) return;

        double r = NOISE.GetNoise((float) (a.x() + b.x()), (float) ((a.y() + b.y())));

        double d = FRACTAL_STRENGTH * r;

        double aY = a.y();
        double aX = a.x();
        double bY = b.y();
        double bX = b.x();

        //this makes sure that we do not care in which direction we go through an edge
        double dx, dy;
        if (aX < bX) {
            dx = aX - bX;
            dy = aY - bY;
        } else {
            dx = bX - aX;
            dy = bY - aY;
        }

        var m = new Point((aX + bX) / 2 + dy * d, (aY + bY) / 2 - dx * d);

        collectFractally(a, m, iterations - 1, result);
        result.add(Project.fromPointToLatLng(m));
        collectFractally(m, b, iterations - 1, result);
    }

    public static double distance(LngLat p1, LngLat p2) {
        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(p2.lat() - p1.lat());
        double lonDistance = Math.toRadians(p2.lng() - p1.lng());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(p1.lat())) * Math.cos(Math.toRadians(p2.lat())) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // convert to meters
    }
}
