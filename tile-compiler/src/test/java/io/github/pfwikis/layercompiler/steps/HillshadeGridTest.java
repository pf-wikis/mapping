package io.github.pfwikis.layercompiler.steps;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.File;
import java.util.List;

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
}
