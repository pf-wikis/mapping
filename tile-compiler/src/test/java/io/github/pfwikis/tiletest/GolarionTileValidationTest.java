package io.github.pfwikis.tiletest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Post-compilation sanity checks for the generated PMTiles archive.
 * Each test targets a well-known Pathfinder feature at zoom 5 — the sweet spot
 * where nations, major cities, and global geography are all visible.
 *
 * <p>These tests act as executable documentation: if they pass, the tileset has
 * real, correctly-placed data; if one fails, a specific layer / region is broken.
 */
@EnabledIf("io.github.pfwikis.tiletest.GolarionTileValidationTest#tilesFileExists")
class GolarionTileValidationTest {

    static final Path TILES = Paths.get("..", "frontend", "public", "golarion.pmtiles").toAbsolutePath();

    static boolean tilesFileExists() {
        return java.nio.file.Files.exists(TILES);
    }

    // ------------------------------------------------------------------
    // 1. Absalom — the most famous city in the setting, used for AR dating.
    //    Tile z5 x15 y12 covers the central Inner Sea where Absalom sits.
    // ------------------------------------------------------------------
    @Test
    void absalomExistsNearCenterOfWorld() throws Exception {
        try (var t = TileAssertions.open(TILES)) {
            t.tile(5, 15, 12)
                .layer("locations")
                .assertFeatureCountMin(1)
                .assertFeatureNear(30.89, -0.23, 150)
                .assertProperty("label", "Absalom");
        }
    }

    // ------------------------------------------------------------------
    // 2. Korvosa — major Chelish port in Varisia.
    //    Tile z5 x14 y11 covers north-western Varisia.
    // ------------------------------------------------------------------
    @Test
    void korvosaExistsInVarisia() throws Exception {
        try (var t = TileAssertions.open(TILES)) {
            t.tile(5, 14, 11)
                .layer("locations")
                .assertFeatureCountMin(1)
                .assertFeatureNear(43.37, -18.75, 150)
                .assertProperty("label", "Korvosa");
        }
    }

    // ------------------------------------------------------------------
    // 3. Magnimar — major Varisian city-state west of Korvosa.
    //    Tile z5 x13 y11 covers south-western Varisia / Magnimar.
    // ------------------------------------------------------------------
    @Test
    void magnimarExistsInVarisia() throws Exception {
        try (var t = TileAssertions.open(TILES)) {
            t.tile(5, 13, 11)
                .layer("locations")
                .assertFeatureCountMin(1)
                .assertFeatureNear(43.93, -28.02, 150)
                .assertProperty("label", "Magnimar");
        }
    }

    // ------------------------------------------------------------------
    // 4. Cheliax — devil-worshipping nation with distinctive borders.
    //    Verifies nation borders carry correct metadata (borderType = 3).
    //    Tile z5 x14 y12 covers the Chelish heartland.
    // ------------------------------------------------------------------
    @Test
    void cheliaxNationBorderExists() throws Exception {
        try (var t = TileAssertions.open(TILES)) {
            t.tile(5, 14, 12)
                .layer("borders")
                .assertFeatureCountMin(5)
                .assertFeatureNear(40.0, -22.0, 400)
                .assertProperty("borderType", 3L);   // 3 = nation border
        }
    }

    // ------------------------------------------------------------------
    // 5. Inner Sea — the single most recognizable body of water on Golarion.
    //    Checks the merged labels layer still contains the water label.
    //    Tile z5 x15 y12 covers the central Inner Sea around Absalom.
    // ------------------------------------------------------------------
    @Test
    void innerSeaLabelExists() throws Exception {
        try (var t = TileAssertions.open(TILES)) {
            t.tile(5, 15, 12)
                .layer("labels")
                .assertFeatureCountMin(10)
                .assertFeatureNear(30.0, 0.0, 400)
                .assertProperty("label", "Inner Sea");
        }
    }

    // ------------------------------------------------------------------
    // 6. Obari Ocean — southern hemisphere water label.
    //    Prevents accidental northern-hemisphere-only regressions in projection
    //    or export bounds. Tile z5 x18 y15 covers the eastern Obari Ocean.
    // ------------------------------------------------------------------
    @Test
    void obariOceanLabelExists() throws Exception {
        try (var t = TileAssertions.open(TILES)) {
            t.tile(5, 18, 15)
                .layer("labels")
                .assertFeatureCountMin(3)
                .assertFeatureNear(10.0, 25.0, 1200)
                .assertProperty("label", "Obari Ocean");
        }
    }
}
