package io.github.pfwikis.layercompiler.steps.validation;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import io.github.pfwikis.layercompiler.description.Ctx;

/**
 * Base class for JUnit 5 tests that assert over a compiled PMTiles archive.
 *
 * <p>Subclasses override {@link #archivePath()} to point at the PMTiles file
 * produced by the pipeline, then write ordinary JUnit test methods using the
 * assertion helpers on {@link #validator}.
 *
 * <pre>
 * public class GolarionTileValidationTest extends TileValidationTestBase {
 *     &#64;Override protected Path archivePath() {
 *         return Path.of("../frontend/public/golarion.pmtiles");
 *     }
 *
 *     &#64;Test public void landShouldExist() {
 *         validator.assertLayerExists("land");
 *     }
 *
 *     &#64;Test public void locationsShouldHaveAbsalom() {
 *         validator.withDefaultZoom(6)
 *                  .assertFeatureNear("locations", 32.0, 45.0, 50.0);
 *     }
 * }
 * </pre>
 */
public abstract class TileValidationTestBase {

    protected TileValidator validator;

    /** Return the path to the PMTiles file under test. */
    protected abstract Path archivePath();

    /** Override to tweak the default zoom (defaults to 5). */
    protected int defaultZoom() {
        return 5;
    }

    @BeforeEach
    void openArchive() throws IOException {
        validator = TileValidator.open(archivePath())
            .withDefaultZoom(defaultZoom());
    }

    @AfterEach
    void closeArchive() throws Exception {
        if (validator != null) {
            validator.close();
        }
    }
}
