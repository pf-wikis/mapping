package io.github.pfwikis.layercompiler.steps.validation;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Concrete test suite for the Golarion PMTiles archive.
 *
 * <p>Run after {@code mvn compile package} (or the full pipeline) has produced
 * {@code ../frontend/public/golarion.pmtiles}.
 *
 * <p>Usage:
 * <pre>{@code
 *   mvn -B test -Dtest=GolarionTileValidationTest
 * }</pre>
 */
class GolarionTileValidationTest extends TileValidationTestBase {

    @Override
    protected Path archivePath() {
        return Path.of("../frontend/public/golarion.pmtiles");
    }

    @Override
    protected int defaultZoom() {
        return 5;
    }

    @Test
    void landLayerShouldExist() {
        validator.assertLayerExists("land");
    }

    @Test
    void bordersLayerShouldExist() {
        validator.assertLayerExists("borders");
    }

    @Test
    void labelsLayerShouldExist() {
        validator.assertLayerExists("labels");
    }

    @Test
    void geometryLayerShouldExist() {
        validator.assertLayerExists("geometry");
    }

    @Test
    void locationsLayerShouldExist() {
        validator.assertLayerExists("locations");
    }

    @Test
    void riversLayerShouldExist() {
        validator.assertLayerExists("rivers");
    }

    @Test
    void geometryShouldHaveReasonableFeatureCountWorldTile() {
        // Roughly the centre of the Golarion map; adjust lat/lon if needed
        validator.withDefaultZoom(3)
                 .assertFeatureCount("geometry", 1, 10_000);
    }

    @Test
    void locationsShouldHaveFeatures() {
        validator.withDefaultZoom(4)
                 .assertFeatureCount("locations", 1, 5000);
    }
}
