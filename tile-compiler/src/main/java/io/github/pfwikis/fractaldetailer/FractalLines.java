package io.github.pfwikis.fractaldetailer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.pfwikis.fractaldetailer.model.Edge;
import io.github.pfwikis.fractaldetailer.model.LngLat;
import io.github.pfwikis.util.Projection;

public class FractalLines {
    private final static double FRACTAL_STRENGTH = 0.2;
    private final static float FRACTAL_SCALE = 10;
    private final static FastNoiseLite NOISE = new FastNoiseLite(7, FRACTAL_SCALE);

    public static List<LngLat> interpolate(List<LngLat> points, Set<Edge> innerEdges, double maxDist) {
        points = points.stream().map(ll->new LngLat(ll.lng(), Projection.geoToMercator(ll.lat()))).toList();
        ArrayDeque<LngLat> open = new ArrayDeque<>(points);
        List<LngLat> result = new ArrayList<>();
        result.add(open.pop());
        LngLat a = result.get(0);

        while (!open.isEmpty()) {
            LngLat b = open.pop();
            double distance = distance(a, b);

            if (distance > maxDist && !isInnerEdge(a, b, innerEdges)) {
                collectFractally(a, b, (int) Math.ceil(Math.log(distance / maxDist) / Math.log(2)), result);
            }
            a = b;
            result.add(b);
        }
        result = result.stream().map(ll->new LngLat(ll.lng(), Projection.mercatorToGeo(ll.lat()))).toList();
        return result;
    }

    private static boolean isInnerEdge(LngLat a, LngLat b, Set<Edge> innerEdges) {
        return innerEdges.contains(new Edge(a, b)) || (a.lng() == 180.0 && b.lng() == 180.0) || (a.lng() == -180.0 && b.lng() == -180.0);
    }

    private static void collectFractally(LngLat a, LngLat b, int iterations, List<LngLat> result) {
        if (iterations == 0) return;

        double r = NOISE.getNoise((float) (a.lng() + b.lng()), (float) ((a.lat() + b.lat())));

        double d = FRACTAL_STRENGTH * r;

        double aY = a.lat();
        double aX = a.lng();
        double bY = b.lat();
        double bX = b.lng();

        //this makes sure that we do not care in which direction we go through an edge
        double dx, dy;
        if (aX < bX) {
            dx = aX - bX;
            dy = aY - bY;
        } else {
            dx = bX - aX;
            dy = bY - aY;
        }

        var m = new LngLat((aX + bX) / 2 + dy * d, (aY + bY) / 2 - dx * d);

        collectFractally(a, m, iterations - 1, result);
        result.add(m);
        collectFractally(m, b, iterations - 1, result);
    }

    /*simple distance since this is a graphical upgrade*/
    public static double distance(LngLat p1, LngLat p2) {
        return Math.sqrt(
            (p1.lat()-p2.lat())*(p1.lat()-p2.lat())
            +(p1.lng()-p2.lng())*(p1.lng()-p2.lng())
        );
    }
}
