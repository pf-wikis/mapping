package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.model.Edge;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.MultiPolygon;
import io.github.pfwikis.model.Geometry.Polygon;
import io.github.pfwikis.model.LngLat;

public class AddDetails {
    public static void main(String[] args) throws IOException {
        FractalLines.MAX_DIST = Integer.parseInt(args[0]);
        args = Arrays.copyOfRange(args, 1, args.length);

        for (String file : args) {
            System.out.println("  detailing " + file);
            var col = new ObjectMapper().readValue(new File(file), FeatureCollection.class);

            System.out.println("    collect Loops");
            var loops = collectLoops(col);
            System.out.println("    found " + loops.size() + " loops");
            System.out.println("    collect inner edges");
            var innerEdges = collectInnerEdges(loops);
            System.out.println("    found " + innerEdges.size() + " inner edges");

            int counter = 0;
            for (var loop : loops) {
                var result = FractalLines.interpolate(loop, innerEdges);
                System.out.println("    " + counter++ + "/" + loops.size());
                loop.clear();
                loop.addAll(result);
            }

            new ObjectMapper().writeValue(new File(file), col);
        }
    }

    private static Set<Edge> collectInnerEdges(List<List<LngLat>> loops) {
        var edges = new HashSet<Edge>();
        var innerEdges = new HashSet<Edge>();

        for (var loop : loops) {
            for (int i = 0; i < loop.size() - 1; i++) {
                Edge e1 = new Edge(loop.get(i), loop.get(i + 1));
                if (!edges.add(e1)) {
                    innerEdges.add(e1);
                }
                Edge e2 = new Edge(loop.get(i + 1), loop.get(i));
                if (!edges.add(e2)) {
                    innerEdges.add(e2);
                }
            }
        }
        return innerEdges;
    }

    private static List<List<LngLat>> collectLoops(FeatureCollection col) {
        var loops = new ArrayList<List<LngLat>>();
        for (var feature : col.getFeatures()) {
            if (feature.getGeometry() instanceof Polygon geom) {
                loops.addAll(geom.getCoordinates());
            } else if (feature.getGeometry() instanceof MultiPolygon geom) {
                geom.getCoordinates().forEach(loops::addAll);
            } else {
                throw new RuntimeException("Unknown type " + feature.getGeometry().getClass());
            }
        }
        return loops;
    }
}
