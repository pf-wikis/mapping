package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.geojson.FeatureCollection;
import org.geojson.LngLatAlt;
import org.geojson.MultiPolygon;
import org.geojson.Polygon;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AddDetails {
    public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException {
        var f = new File("../geo/forests.geojson");
        var col = new ObjectMapper().readValue(f, FeatureCollection.class);

        AtomicInteger counter = new AtomicInteger();
        for(var feature : col.getFeatures()) {
            if(feature.getGeometry() instanceof Polygon geom) {
                handlePolygon(geom.getCoordinates());
            }
            else if(feature.getGeometry() instanceof MultiPolygon geom) {
                geom.getCoordinates().forEach(AddDetails::handlePolygon);
            }
            else {
                throw new RuntimeException("Unknown type "+feature.getGeometry().getClass());
            }
            System.out.println(counter.incrementAndGet()+"/"+col.getFeatures().size());
        }

        new ObjectMapper().writeValue(f, col);
    }

    private static void handlePolygon(List<List<LngLatAlt>> polygon) {
        polygon.forEach(AddDetails::handleLoop);
    }

    private static void handleLoop(List<LngLatAlt> loop) {
        var result = FractalLines.interpolate(loop, false);
        loop.clear();
        loop.addAll(result);
    }
}
