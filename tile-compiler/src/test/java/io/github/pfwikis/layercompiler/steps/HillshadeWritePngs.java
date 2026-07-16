package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.Jackson;

public class HillshadeWritePngs {
    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : "/tmp";
        GeoJsonReader geoReader = new GeoJsonReader();

        // Mountains
        File mountainsFile = findFile("mountains.geojson");
        if (mountainsFile != null) {
            FeatureCollection fc = Jackson.JSON.readValue(mountainsFile, FeatureCollection.class);
            List<org.locationtech.jts.geom.Geometry> polygons = new ArrayList<>();
            for (var f : fc.getFeatures()) {
                var geom = convertToJts(f, geoReader);
                if (geom != null && !geom.isEmpty()) polygons.add(geom);
            }

            List<Coordinate> pointBumps = new ArrayList<>();
            File locationsFile = findFile("locations.geojson");
            if (locationsFile != null) {
                FeatureCollection locs = Jackson.JSON.readValue(locationsFile, FeatureCollection.class);
                for (var f : locs.getFeatures()) {
                    if ("mountain".equals(f.getProperties().getType())) {
                        var geom = f.getGeometry();
                        if (geom instanceof io.github.pfwikis.model.Geometry.Point p) {
                            pointBumps.add(new Coordinate(p.getCoordinates().lng(), p.getCoordinates().lat()));
                        }
                    }
                }
            }

            HillshadeGrid grid = new HillshadeGrid(2048, 1024, -70, 100, -40, 42,
                    polygons, 255, 0.7, pointBumps, 200.0, 0.3);

            File png = new File(outputDir, "hillshade-mountains.png");
            grid.writeHillshade(png);
            System.out.println("Wrote " + png.getAbsolutePath() + " (" + png.length() + " bytes)");
        }

        // Hills
        File hillsFile = findFile("hills.geojson");
        if (hillsFile != null) {
            FeatureCollection fc = Jackson.JSON.readValue(hillsFile, FeatureCollection.class);
            List<org.locationtech.jts.geom.Geometry> polygons = new ArrayList<>();
            for (var f : fc.getFeatures()) {
                var geom = convertToJts(f, geoReader);
                if (geom != null && !geom.isEmpty()) polygons.add(geom);
            }

            HillshadeGrid grid = new HillshadeGrid(2048, 1024, -70, 100, -40, 42,
                    polygons, 128, 1.0, List.of(), 0, 0);

            File png = new File(outputDir, "hillshade-hills.png");
            grid.writeHillshade(png);
            System.out.println("Wrote " + png.getAbsolutePath() + " (" + png.length() + " bytes)");
        }
    }

    private static org.locationtech.jts.geom.Geometry convertToJts(
            io.github.pfwikis.model.Feature f, GeoJsonReader reader) throws Exception {
        if (f == null || f.getGeometry() == null) return null;
        String json = Jackson.JSON.writeValueAsString(f.getGeometry());
        return reader.read(json);
    }

    private static File findFile(String name) {
        String[] paths = {
            System.getProperty("java.io.tmpdir") + "/" + name,
            "/tmp/" + name,
            "/mnt/c/Users/manuel.hegner/AppData/Local/Temp/" + name,
            "/mnt/c/Users/manuel/AppData/Local/Temp/" + name,
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.length() > 0) return f;
        }
        return null;
    }
}
