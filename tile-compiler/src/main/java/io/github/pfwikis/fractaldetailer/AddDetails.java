package io.github.pfwikis.fractaldetailer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.fractaldetailer.model.Edge;
import io.github.pfwikis.fractaldetailer.model.FeatureCollection;
import io.github.pfwikis.fractaldetailer.model.Geometry.MultiPolygon;
import io.github.pfwikis.fractaldetailer.model.Geometry.Polygon;
import io.github.pfwikis.fractaldetailer.model.LngLat;

public class AddDetails {
    public static void main(String[] args) throws IOException {
        int maxDistance = Integer.parseInt(args[0]);
        args = Arrays.copyOfRange(args, 1, args.length);

        for (String file : args) {
            var bytes = addDetails(maxDistance, FileUtils.readFileToByteArray(new File(file)));
            FileUtils.writeByteArrayToFile(new File(file), bytes);
        }
    }

    public static byte[] addDetails(int maxDistance, byte[] in) throws IOException {
        FractalLines.MAX_DIST = maxDistance;
        System.out.println("  detailing " + in);
        var col = new ObjectMapper().readValue(in, FeatureCollection.class);

        System.out.println("    collect Loops");
        var loops = collectLoops(col);
        System.out.println("    found " + loops.size() + " loops");
        System.out.println("    collect inner edges");
        var innerEdges = collectInnerEdges(loops);
        System.out.println("    found " + innerEdges.size() + " inner edges");

        for (var loop : loops) {
            var result = FractalLines.interpolate(loop, innerEdges);
            loop.clear();
            loop.addAll(result);
        }

        var baos = new ByteArrayOutputStream();
        new ObjectMapper().writeValue(baos, col);
        return baos.toByteArray();
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
