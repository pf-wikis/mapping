package io.github.pfwikis.layercompiler.steps;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.Jackson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: reads real mountains/hills GeoJSON and generates hillshade PNGs.
 * Requires extracted GeoJSON files from the GeoPackage (run externally via ogr2ogr).
 * Files expected at system property paths or skipped gracefully.
 */
public class HillshadeIntegrationTest {

    private GeoJsonReader geoReader = new GeoJsonReader();
    private GeometryFactory gf = new GeometryFactory();

    @Test
    public void testMountainHillshadeFromRealData() throws Exception {
        File mountainsFile = findFile("mountains.geojson");
        if (mountainsFile == null) {
            return; // skip if no real data available
        }

        FeatureCollection fc = Jackson.JSON.readValue(mountainsFile, FeatureCollection.class);
        List<org.locationtech.jts.geom.Geometry> polygons = new ArrayList<>();
        for (var f : fc.getFeatures()) {
            var geom = convertToJts(f);
            if (geom != null && !geom.isEmpty()) {
                polygons.add(geom);
            }
        }

        assertFalse(polygons.isEmpty(), "Should have mountain polygons");
        System.out.println("Mountain polygons: " + polygons.size());

        // parse locations for mountain point bumps
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
            System.out.println("Mountain point bumps: " + pointBumps.size());
        }

        HillshadeGrid grid = new HillshadeGrid(
            2048, 1024,
            -70, 100, -40, 42,
            polygons, 255, 0.7,
            pointBumps, 200.0, 0.3
        );

        File pngFile = File.createTempFile("hillshade-mountains", ".png");
        pngFile.deleteOnExit();
        grid.writeHillshade(pngFile);

        System.out.println("Wrote " + pngFile.getAbsolutePath() + " (" + pngFile.length() + " bytes)");
        assertTrue(pngFile.exists() && pngFile.length() > 0);

        File wldFile = new File(pngFile.getParent(), pngFile.getName().replace(".png", ".wld"));
        assertTrue(wldFile.exists() && wldFile.length() > 0);
    }

    @Test
    public void testHillHillshadeFromRealData() throws Exception {
        File hillsFile = findFile("hills.geojson");
        if (hillsFile == null) {
            return; // skip if no real data available
        }

        FeatureCollection fc = Jackson.JSON.readValue(hillsFile, FeatureCollection.class);
        List<org.locationtech.jts.geom.Geometry> polygons = new ArrayList<>();
        for (var f : fc.getFeatures()) {
            var geom = convertToJts(f);
            if (geom != null && !geom.isEmpty()) {
                polygons.add(geom);
            }
        }

        assertFalse(polygons.isEmpty(), "Should have hill polygons");
        System.out.println("Hill polygons: " + polygons.size());

        HillshadeGrid grid = new HillshadeGrid(
            2048, 1024,
            -70, 100, -40, 42,
            polygons, 128, 1.0,
            List.of(), 0, 0
        );

        File pngFile = File.createTempFile("hillshade-hills", ".png");
        pngFile.deleteOnExit();
        grid.writeHillshade(pngFile);

        System.out.println("Wrote " + pngFile.getAbsolutePath() + " (" + pngFile.length() + " bytes)");
        assertTrue(pngFile.exists() && pngFile.length() > 0);
    }

    private File findFile(String name) {
        // Check temp dirs and known locations
        String[] paths = {
            System.getProperty("java.io.tmpdir") + "/" + name,
            "/tmp/" + name,
            "/home/manuel/workspace-priv/mapping/sources/" + name,
            "/mnt/c/Users/manuel.hegner/AppData/Local/Temp/" + name,
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    private org.locationtech.jts.geom.Geometry convertToJts(io.github.pfwikis.model.Feature f) throws Exception {
        if (f == null || f.getGeometry() == null) return null;
        var modelGeom = f.getGeometry();
        // Serialize to GeoJSON and re-parse with JTS reader
        String json = Jackson.JSON.writeValueAsString(modelGeom);
        return geoReader.read(json);
    }
}
