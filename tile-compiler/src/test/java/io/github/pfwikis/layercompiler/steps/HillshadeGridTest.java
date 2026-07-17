package io.github.pfwikis.layercompiler.steps;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

public class HillshadeGridTest {

    @Test
    public void testSimplePolygon() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid = new HillshadeGrid(
            100, 100,
            List.of(poly),
            255, 1.0,
            List.of(), 0, 0
        );

        File tmp = File.createTempFile("hillshade", ".png");
        tmp.deleteOnExit();
        grid.writeHillshade(tmp);

        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 0);

        File wld = new File(tmp.getParent(), tmp.getName().replace(".png", ".wld"));
        assertTrue(wld.exists());
    }

    @Test
    public void testWithPointBump() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid = new HillshadeGrid(
            100, 100,
            List.of(poly),
            255, 1.0,
            List.of(new Coordinate(5, 5)), 200, 0.5
        );

        File tmp = File.createTempFile("hillshade_bump", ".png");
        tmp.deleteOnExit();
        grid.writeHillshade(tmp);
        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 0);
    }

    @Test
    public void testDeterminism() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid1 = new HillshadeGrid(100, 100, List.of(poly), 255, 1.0, List.of(), 0, 0);
        HillshadeGrid grid2 = new HillshadeGrid(100, 100, List.of(poly), 255, 1.0, List.of(), 0, 0);

        File tmp1 = File.createTempFile("hs1", ".png");
        File tmp2 = File.createTempFile("hs2", ".png");
        tmp1.deleteOnExit();
        tmp2.deleteOnExit();
        grid1.writeHillshade(tmp1);
        grid2.writeHillshade(tmp2);

        byte[] b1 = java.nio.file.Files.readAllBytes(tmp1.toPath());
        byte[] b2 = java.nio.file.Files.readAllBytes(tmp2.toPath());
        assertArrayEquals(b1, b2, "Hillshade output should be deterministic");
    }

    @Test
    public void testTerrariumRoundTrip() {
        for (int elev = 0; elev <= 255; elev++) {
            int[] rgb = HillshadeGrid.encodeTerrarium(elev);
            int decoded = HillshadeGrid.decodeTerrarium(rgb[0], rgb[1], rgb[2]);
            assertEquals(elev, decoded, "Terrarium round-trip failed for elevation " + elev);
        }
    }

    @Test
    public void testDemTiles() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid = new HillshadeGrid(
            100, 100,
            List.of(poly),
            255, 1.0,
            List.of(), 0, 0
        );

        File tmpDir = File.createTempFile("hillshade_tiles", "");
        tmpDir.delete();
        tmpDir.mkdirs();

        grid.writeDemTiles(tmpDir, "test", 1, 256);

        // Check that tiles were created for z=0 and z=1
        File z0Dir = new File(tmpDir, "hillshade-test/0");
        assertTrue(z0Dir.exists() && z0Dir.isDirectory(), "z=0 directory should exist");

        File z1Dir = new File(tmpDir, "hillshade-test/1");
        assertTrue(z1Dir.exists() && z1Dir.isDirectory(), "z=1 directory should exist");

        // Verify at least one tile is a valid PNG
        File[] z0Tiles = z0Dir.listFiles();
        assertNotNull(z0Tiles);
        assertTrue(z0Tiles.length > 0, "At least one z=0 tile should exist");

        File tileFile = z0Tiles[0];
        assertTrue(tileFile.listFiles().length > 0, "Tile file should exist");
    }

    @Test
    public void testDemTileContainsElevationData() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        // Large square from -20,-20 to 20,20 centered near lat=0,lon=0
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(-20, -20),
            new Coordinate(20, -20),
            new Coordinate(20, 20),
            new Coordinate(-20, 20),
            new Coordinate(-20, -20)
        });

        HillshadeGrid grid = new HillshadeGrid(
            200, 200,
            -30, 30, -30, 30,
            List.of(poly), 255, 1.0,
            List.of(), 0, 0
        );

        File tmpDir = File.createTempFile("hillshade_elev", "");
        tmpDir.delete();
        tmpDir.mkdirs();

        grid.writeDemTiles(tmpDir, "elev", 0, 256);

        File tile0 = new File(tmpDir, "hillshade-elev/0/0/0.png");
        assertTrue(tile0.exists() && tile0.length() > 0, "z=0/0/0 tile should exist");

        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(tile0);
        assertNotNull(img);
        assertEquals(256, img.getWidth());
        assertEquals(256, img.getHeight());

        // Sample near center of tile (corresponds to lat ~0, lon ~0 which should be inside polygon)
        int rgb = img.getRGB(128, 128);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int decoded = HillshadeGrid.decodeTerrarium(red, green, blue);
        assertTrue(decoded > 10, "Center pixel should have non-trivial elevation, got " + decoded);
    }

    @Test
    public void testEmptyTileSkipping() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        // Single small polygon far from the southern hemisphere tile area
        // z=0 tile covers whole world; z=1 tile covers half the world.
        // We place polygon at lat/lon [80,80]—[85,85] (near the North Pole).
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(80, 80),
            new Coordinate(85, 80),
            new Coordinate(85, 85),
            new Coordinate(80, 85),
            new Coordinate(80, 80)
        });

        HillshadeGrid grid = new HillshadeGrid(
            200, 200,
            70, 95, 70, 95,
            List.of(poly), 255, 1.0,
            List.of(), 0, 0
        );

        File tmpDir = File.createTempFile("hillshade_empty", "");
        tmpDir.delete();
        tmpDir.mkdirs();

        grid.writeDemTiles(tmpDir, "empty", 1, 256);

        // In z=1 there are 2x2=4 tiles.
        // The polygon is in the NE tile (x=1, y=0) since lat>0 & lon>0.
        // The other 3 tiles (south-east, south-west, north-west) should all be empty/skipped.
        int z1TileCount = 0;
        File z1Dir = new File(tmpDir, "hillshade-empty/1");
        if (z1Dir.exists() && z1Dir.isDirectory()) {
            for (File xDir : z1Dir.listFiles()) {
                if (xDir.isDirectory()) {
                    for (File f : xDir.listFiles()) {
                        if (f.isFile() && f.getName().endsWith(".png")) {
                            z1TileCount++;
                            // Every created tile should be a valid 256x256 PNG
                            BufferedImage img = ImageIO.read(f);
                            assertNotNull(img, f.getAbsolutePath() + " should be a valid PNG");
                            assertEquals(256, img.getWidth());
                            assertEquals(256, img.getHeight());
                        }
                    }
                }
            }
        }
        // Only the NE tile should have been written (maxElev >= 1 test in createDemTile)
        assertEquals(1, z1TileCount, "Only one z=1 tile should contain elevation data");
    }
}
