package io.github.pfwikis.layercompiler.steps;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DEM tile generation: tile structure, terrarium pixel data, and multi-zoom consistency.
 */
public class HillshadeTileTest {

    private static final int TILE_SIZE = 256;

    @Test
    public void testTileBoundsMatch() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        // Small 5x5 square centered at origin (inside [-10,10] bounds)
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(-2.5, -2.5),
            new Coordinate(2.5, -2.5),
            new Coordinate(2.5, 2.5),
            new Coordinate(-2.5, 2.5),
            new Coordinate(-2.5, -2.5)
        });

        HillshadeGrid grid = new HillshadeGrid(
            200, 200,
            -10, 10, -10, 10,
            List.of(poly), 255, 1.0,
            List.of(), 0, 0
        );

        File tmpDir = File.createTempFile("hillshade_tiles_bounds", "");
        tmpDir.delete();
        tmpDir.mkdirs();

        for (int zoom : new int[]{2, 3, 4}) {
            File zoomDir = new File(tmpDir, "hillshade-bounds" + zoom);
            zoomDir.mkdirs();
            grid.writeDemTiles(zoomDir, "layer" + zoom, zoom, TILE_SIZE);

            File baseDir = new File(zoomDir, "hillshade-layer" + zoom);
            assertTrue(baseDir.exists() && baseDir.isDirectory(),
                    "Base directory should exist for zoom " + zoom);

            // collect every tile at every x level
            int[] tileCount = {0};
            java.util.Deque<File> stack = new java.util.ArrayDeque<>();
            stack.push(baseDir);
            while (!stack.isEmpty()) {
                File f = stack.pop();
                if (f.isFile() && f.getName().endsWith(".png")) {
                    tileCount[0]++;
                    // verify valid PNG and dimensions
                    BufferedImage img = ImageIO.read(f);
                    assertNotNull(img, "Tile should be a valid PNG: " + f.getAbsolutePath());
                    assertEquals(TILE_SIZE, img.getWidth(), "Width mismatch for " + f.getName());
                    assertEquals(TILE_SIZE, img.getHeight(), "Height mismatch for " + f.getName());
                } else if (f.isDirectory()) {
                    File[] children = f.listFiles();
                    if (children != null) {
                        for (File child : children) stack.push(child);
                    }
                }
            }
            assertTrue(tileCount[0] > 0, "At least one tile should exist at zoom " + zoom);
        }
    }

    @Test
    public void testTerrariumTilesContainElevationData() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        // Large polygon covering [0,0] to [10,10]
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid = new HillshadeGrid(
            200, 200,
            -5, 15, -5, 15,
            List.of(poly), 255, 1.0,
            List.of(), 0, 0
        );

        File tmpDir = File.createTempFile("hillshade_tiles_data", "");
        tmpDir.delete();
        tmpDir.mkdirs();

        grid.writeDemTiles(tmpDir, "data", 2, TILE_SIZE);

        // Find a non-empty tile created under hillshade-data/2
        File z2Dir = new File(tmpDir, "hillshade-data/2");
        assertTrue(z2Dir.exists() && z2Dir.isDirectory(), "z=2 directory should exist");

        File candidate = findAnyPng(z2Dir);
        assertNotNull(candidate, "At least one tile should exist at zoom 2");
        assertTrue(candidate.length() > 0, "Tile should have content");

        BufferedImage img = ImageIO.read(candidate);
        assertNotNull(img);
        assertEquals(TILE_SIZE, img.getWidth());
        assertEquals(TILE_SIZE, img.getHeight());

        // Search within the tile for a pixel with non-zero elevation.
        // Since our polygon is small inside a global tile, find max elevation.
        int maxDecoded = 0;
        for (int row = 0; row < TILE_SIZE; row++) {
            for (int col = 0; col < TILE_SIZE; col++) {
                int rgb = img.getRGB(col, row);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int decoded = HillshadeGrid.decodeTerrarium(red, green, blue);
                if (decoded > maxDecoded) maxDecoded = decoded;
            }
        }
        assertTrue(maxDecoded > 50,
                "Tile should contain at least one pixel with meaningful elevation, got max=" + maxDecoded);
    }

    private static File findAnyPng(File dir) {
        java.util.Deque<File> stack = new java.util.ArrayDeque<>();
        stack.push(dir);
        while (!stack.isEmpty()) {
            File f = stack.pop();
            if (f.isFile() && f.getName().endsWith(".png")) return f;
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File c : kids) stack.push(c);
                }
            }
        }
        return null;
    }

    @Test
    public void testMultiZoomConsistency() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        // Polygon -5,-5 to 5,5 inside bounds -20,20 -20,20
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(-5, -5),
            new Coordinate(5, -5),
            new Coordinate(5, 5),
            new Coordinate(-5, 5),
            new Coordinate(-5, -5)
        });

        HillshadeGrid grid = new HillshadeGrid(
            400, 400,
            -20, 20, -20, 20,
            List.of(poly), 255, 1.0,
            List.of(), 0, 0
        );

        File tmpDir = File.createTempFile("hillshade_tiles_multi", "");
        tmpDir.delete();
        tmpDir.mkdirs();

        // Generate tiles for zoom 2 and zoom 3
        grid.writeDemTiles(tmpDir, "multi2", 2, TILE_SIZE);
        grid.writeDemTiles(tmpDir, "multi3", 3, TILE_SIZE);

        // Pick a geographic point well inside the polygon: (1, 1)
        double testLon = 1.0;
        double testLat = 1.0;

        int elev2 = readElevationAtPoint(tmpDir, "multi2", 2, testLon, testLat);
        int elev3 = readElevationAtPoint(tmpDir, "multi3", 3, testLon, testLat);

        assertTrue(elev2 > 10, "Zoom 2 elevation at (1,1) should be positive, got " + elev2);
        assertTrue(elev3 > 10, "Zoom 3 elevation at (1,1) should be positive, got " + elev3);
        // Same underlying DEM sampled at same geo point → values should match within reasonable
        // tolerance because different zooms sample slightly different pixel centers and apply
        // bilinear interpolation.
        assertEquals(elev2, elev3, 15,
                "Elevation at (1,1) should be consistent across zooms; z2=" + elev2 + " z3=" + elev3);
    }

    // Helper: from the tile pyramid written by writeDemTiles, read the terrarium-encoded
    // elevation at a precise lat/lon using the same XYZ math used by HillshadeGrid.
    private int readElevationAtPoint(File tmpDir, String layer, int zoom, double lon, double lat)
            throws Exception {

        // Tile indices
        int n = 1 << zoom;
        double latRad = Math.toRadians(lat);
        int tx = (int) Math.floor(((lon + 180.0) / 360.0) * n);
        int ty = (int) Math.floor(
                (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);

        File tileFile = new File(tmpDir, "hillshade-" + layer + "/" + zoom + "/" + tx + "/" + ty + ".png");
        assertTrue(tileFile.exists(), "Tile file should exist: " + tileFile.getAbsolutePath());

        BufferedImage img = ImageIO.read(tileFile);
        assertNotNull(img);

        double west = tx / (double) n * 360.0 - 180.0;
        double east = (tx + 1) / (double) n * 360.0 - 180.0;
        double north = tileYToLat(ty, zoom);
        double south = tileYToLat(ty + 1, zoom);

        double px = ((lon - west) / (east - west)) * TILE_SIZE - 0.5;
        double py = ((north - lat) / (north - south)) * TILE_SIZE - 0.5;

        int col = (int) Math.round(px);
        int row = (int) Math.round(py);
        col = Math.max(0, Math.min(TILE_SIZE - 1, col));
        row = Math.max(0, Math.min(TILE_SIZE - 1, row));

        int rgb = img.getRGB(col, row);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return HillshadeGrid.decodeTerrarium(red, green, blue);
    }

    private static double tileYToLat(int y, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * y / (double) (1 << zoom);
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
    }
}
