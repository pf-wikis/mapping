package io.github.pfwikis.layercompiler.steps;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TerrariumEncodingTest {

    @Test
    public void testTerrarium0To255() {
        for (int e = 0; e <= 255; e++) {
            int[] rgb = HillshadeGrid.encodeTerrarium(e);
            int decoded = HillshadeGrid.decodeTerrarium(rgb[0], rgb[1], rgb[2]);
            assertEquals(e, decoded, "Terrarium round-trip failed for elevation " + e);
        }
    }

    @Test
    public void testTerrariumNegativeNotUsed() {
        // Our synthetic elevations are always >= 0, but encodeTerrarium is well-defined for negatives.
        // Document that negative values also round-trip correctly, but our DEM pipeline never produces them.
        int[] rgb = HillshadeGrid.encodeTerrarium(-1);
        int decoded = HillshadeGrid.decodeTerrarium(rgb[0], rgb[1], rgb[2]);
        assertEquals(-1, decoded, "Terrarium should round-trip -1 correctly");
        assertEquals(127, rgb[0]);
        assertEquals(255, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testTerrarium255() {
        int[] rgb = HillshadeGrid.encodeTerrarium(255);
        int decoded = HillshadeGrid.decodeTerrarium(rgb[0], rgb[1], rgb[2]);
        assertEquals(255, decoded);
        // 255 + 32768 = 33023 -> floor(33023/256) = 128, 33023 % 256 = 255
        assertEquals(128, rgb[0]);
        assertEquals(255, rgb[1]);
        assertEquals(0, rgb[2]);
    }
}
