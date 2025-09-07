package io.github.pfwikis.fractaldetailer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.pfwikis.model.Edge;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.MultiPolygon;
import io.github.pfwikis.model.Geometry.Polygon;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.util.Projection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddDetails {
    public static FeatureCollection addDetails(double maxDistance, FeatureCollection in) throws IOException {
        log.info("  detailing by {} ", maxDistance);
        var col = in;
        
        log.info("    collect Loops");
        var loops = collectLoops(col);
        log.info("    found " + loops.size() + " loops");
        log.info("    collect inner edges");
        var innerEdges = collectInnerEdges(loops);
        innerEdges = innerEdges.stream().map(e->new Edge(
            new LngLat(e.a().lng(), Projection.geoToMercator(e.a().lat())),
            new LngLat(e.b().lng(), Projection.geoToMercator(e.b().lat()))
        )).collect(Collectors.toSet());
        log.info("    found " + innerEdges.size() + " inner edges");

        int initialSize = 0;
        int newSize = 0;
        
        for (var loop : loops) {
            var result = FractalLines.interpolate(loop, innerEdges, maxDistance);
            initialSize += loop.size();
            newSize += result.size();
            loop.clear();
            loop.addAll(result);
        }
        
        log.info("    detailed to {}%", (int)(100d*newSize/initialSize));
        
        return col;
    }

    private static Set<Edge> collectInnerEdges(List<List<LngLat>> loops) {
        var edges = new HashSet<Edge>();
        var innerEdges = new HashSet<Edge>();

        for (var loop : loops) {
            for (int i = 0; i < loop.size() - 1; i++) {
                Edge e1 = new Edge(loop.get(i), loop.get(i + 1));
                if (!edges.add(e1.norm())) {
                    innerEdges.add(e1);
                }
                Edge e2 = new Edge(loop.get(i + 1), loop.get(i));
                if (!edges.add(e2.norm())) {
                    innerEdges.add(e2);
                }

                //prevent fractal details on the poles
                if(Math.abs(e1.a().lat())>88 || Math.abs(e1.b().lat())>88) {
                    innerEdges.add(e1);
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
            } else if(feature.getGeometry() == null) {
            	//do nothing
            }else {
                throw new RuntimeException("Unknown type " + feature.getGeometry().getClass());
            }
        }
        return loops;
    }
}
