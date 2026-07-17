package io.github.pfwikis.layercompiler.steps.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;

import com.onthegomap.planetiler.pmtiles.Pmtiles;
import com.onthegomap.planetiler.pmtiles.ReadablePmtiles;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * DAG step that runs after {@code COMPILE_TILES}.<br>
 * It performs a suite of lightweight smoke tests on the generated PMTiles file
 * to make sure the archive looks structurally healthy and contains the expected
 * tiles and features.
 *
 * <p>The list of assertions lives in {@link #VALIDATORS}.
 * Adding a new check is only a matter of appending another
 * {@link KnownFeature} or adjusting a structural threshold below.</p>
 */
@Slf4j
@Getter
@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class ValidateTiles extends StepExecutor {

    /** The PMTiles file to validate — relative to the CLI target directory. */
    private String filename = "golarion";
    private String extension = "pmtiles";

    // ------------------------------------------------------------------
    //  Structural checks (fast, no tile decoding)
    // ------------------------------------------------------------------

    /** Minimum archive size (bytes). A significantly smaller file is suspicious. */
    private long minArchiveSize = 5L * 1024 * 1024;   // 5 MiB

    /** Maximum archive size (bytes). Mostly to catch catastrophic duplication. */
    private long maxArchiveSize = 2L * 1024 * 1024 * 1024;  // 2 GiB

    /** Minimum number of addressed tiles. */
    private long minAddressedTiles = 1000;

    /** Minimum zoom level we expect in the header. */
    private int minZoom = 0;

    /** Maximum zoom level we expect in the header. */
    private int maxZoom = 8;

    /** Expected tile format — MLT in this project. */
    private String expectedTileType = "mlt";

    // ------------------------------------------------------------------
    //  Content checks (sampled tiles + decoded MLT)
    // ------------------------------------------------------------------

    /**
     * Pre-defined smoke-test features. These are all well-known Pathfinder locations
     * that should be present in the map at the given zoom level.
     *
     * <p>The list is intentionally short and uses loose tolerances so that small
     * data changes (new settlements, moved coordinates, etc.) do not break the build.</p>
     */
    public static final List<KnownFeature> VALIDATORS = List.of(
        // City – Absalom (the biggest city on Golarion, iconic anchor point)
        new KnownFeature(
            "Absalom (city)", "locations", "label", "Absalom",
            30.88863, -0.2343082, 150.0, 4, 2, 5
        ),

        // City – Katapesh (major trade hub in northern Garund)
        new KnownFeature(
            "Katapesh (city)", "locations", "label", "Katapesh",
            22.4693, -1.187638, 150.0, 4, 2, 5
        ),

        // City – Korvosa (largest city in Varisia)
        new KnownFeature(
            "Korvosa (city)", "locations", "label", "Korvosa",
            43.37351, -18.74793, 150.0, 4, 2, 5
        ),

        // City – Magnimar (second-largest in Varisia)
        new KnownFeature(
            "Magnimar (city)", "locations", "label", "Magnimar",
            43.92702, -28.02096, 150.0, 4, 2, 5
        ),

        // Nation label – Cheliax (should appear as a polygon/area label at low zoom)
        new KnownFeature(
            "Cheliax (nation label)", "nation-labels", "label", "Cheliax",
            42.0, -8.0, 200.0, 3, 1, 1
        ),

        // Water label – Inner Sea (major geographic feature)
        new KnownFeature(
            "Inner Sea (water label)", "water-labels", "label", "Inner Sea",
            34.0, -5.0, 200.0, 3, 1, 1
        ),

        // Continent label – Avistan
        new KnownFeature(
            "Avistan (continent label)", "continent-labels", "label", "Avistan",
            45.0, -5.0, 300.0, 2, 1, 1
        )
    );

    @Override
    public Content process(Inputs in) throws Exception {
        var archive = resolveArchive();
        if (archive == null) {
            log.info("Skipping tile validation — no PMTiles file found (is this a debug build?).");
            return Content.empty();
        }

        log.info("Running PMTiles validation on {}", archive);

        try (var rawReader = ReadablePmtiles.newReadFromFile(archive);
             var readerNeedsClose = (ReadablePmtiles) rawReader) {
            runStructuralChecks(readerNeedsClose, archive);
            runContentChecks(readerNeedsClose);
        }

        log.info("All PMTile validations passed.");
        return Content.empty();
    }

    /** Resolves the path to the PMTiles archive, or {@code null} if it doesn't exist. */
    private Path resolveArchive() {
        var path = Path.of(
            io.github.pfwikis.layercompiler.description.Ctx.INSTANCE.getOptions().targetDirectory().toString(),
            filename + "." + extension
        );
        return java.nio.file.Files.exists(path) ? path : null;
    }

    // ------------------------------------------------------------------
    //  Structural smoke tests
    // ------------------------------------------------------------------

    private void runStructuralChecks(ReadablePmtiles reader, Path path) throws IOException {
        var header = reader.getHeader();

        // Archive size sanity
        long size = java.nio.file.Files.size(path);
        if (size < minArchiveSize) {
            throw new ValidationError(
                "Archive too small: %s (expected at least %d MiB)"
                    .formatted(
                        java.text.NumberFormat.getInstance().format(size),
                        minArchiveSize / (1024 * 1024)
                    )
            );
        }
        if (size > maxArchiveSize) {
            throw new ValidationError(
                "Archive unexpectedly large: %s (expected at most %d GiB)"
                    .formatted(
                        java.text.NumberFormat.getInstance().format(size),
                        maxArchiveSize / (1024 * 1024 * 1024)
                    )
            );
        }
        log.debug("Archive size: {} bytes", size);

        // Header tile count
        if (header.numAddressedTiles() < minAddressedTiles) {
            throw new ValidationError(
                "Too few addressed tiles: %d (expected >= %d)".formatted(
                    header.numAddressedTiles(), minAddressedTiles
                )
            );
        }
        log.debug("Addressed tiles: {}", header.numAddressedTiles());

        // Zoom range
        int headerMinZ = header.minZoom() & 0xFF;  // stored as unsigned byte
        int headerMaxZ = header.maxZoom() & 0xFF;
        if (headerMinZ > headerMaxZ || headerMinZ > minZoom || headerMaxZ < maxZoom) {
            throw new ValidationError(
                "Unexpected zoom range in header: minZoom=%d, maxZoom=%d".formatted(
                    headerMinZ, headerMaxZ
                )
            );
        }
        log.debug("Header zoom range: {}-{}", headerMinZ, headerMaxZ);

        // Tile format
        Pmtiles.TileType tileType = header.tileType();
        String actual;
        if (tileType == Pmtiles.TileType.MLT) {
            actual = "mlt";
        } else if (tileType == Pmtiles.TileType.MVT) {
            actual = "mvt";
        } else {
            actual = tileType.name().toLowerCase();
        }

        if (!actual.equals(expectedTileType)) {
            throw new ValidationError(
                "Unexpected tile type in header: '%s' (expected '%s')".formatted(actual, expectedTileType)
            );
        }
        log.debug("Tile type: {}", actual);

        // Metadata – list layers
        var jsonMeta = reader.getJsonMetadata();
        var vectorLayers = jsonMeta.vectorLayers();
        if (vectorLayers == null || vectorLayers.isEmpty()) {
            throw new ValidationError("No vector layers found in PMTiles metadata");
        }
        log.debug("Layers in metadata: {}",
            vectorLayers.stream().map(com.onthegomap.planetiler.util.LayerAttrStats.VectorLayer::id).toList()
        );
    }

    // ------------------------------------------------------------------
    //  Content checks (sample tiles, decode to JTS, inspect properties)
    // ------------------------------------------------------------------

    private void runContentChecks(ReadablePmtiles reader) throws IOException {
        // Cache decoded tiles so the same tile isn't decoded twice.
        Map<String, org.maplibre.mlt.data.MapLibreTile> tileCache = new HashMap<>();

        for (var feature : VALIDATORS) {
            checkKnownFeature(reader, tileCache, feature);
        }
    }

    private void checkKnownFeature(
        ReadablePmtiles reader,
        Map<String, org.maplibre.mlt.data.MapLibreTile> tileCache,
        KnownFeature known
    ) throws IOException {
        int z = known.zoom();
        int expectedX = known.tileX();
        int expectedY = known.tileY();

        // Try a neighbourhood of tiles in case of slight projection shifts.
        int tol = known.tileTolerance();
        boolean found = false;
        String lastError = null;

        outer:
        for (int dx = -tol; dx <= tol; dx++) {
            for (int dy = -tol; dy <= tol; dy++) {
                int x = expectedX + dx;
                int y = expectedY + dy;
                if (x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z)) {
                    continue;
                }
                var tile = getCachedTile(reader, tileCache, z, x, y);
                if (tile == null) {
                    continue; // blank tile – normal for water-only or land-only areas
                }
                try {
                    assertTileContains(tile, known);
                    found = true;
                    break outer;
                } catch (ValidationError e) {
                    lastError = e.getMessage();
                }
            }
        }

        if (!found) {
            throw new ValidationError(
                "[%s] not found in layer '%s' near tile z=%d x~%d y~%d. Last error: %s"
                    .formatted(
                        known.name(), known.layer(), z, expectedX, expectedY,
                        lastError != null ? lastError : "tile empty or missing"
                    )
            );
        }

        log.debug("[PASS] {} in layer '{}' (z={} x~{} y~{})",
            known.name(), known.layer(), z, expectedX, expectedY);
    }

    /**
     * Retrieves a decoded MLT tile, using a simple per-step cache keyed by "z/x/y".
     */
    private org.maplibre.mlt.data.MapLibreTile getCachedTile(
        ReadablePmtiles reader,
        Map<String, org.maplibre.mlt.data.MapLibreTile> cache,
        int z, int x, int y
    ) throws IOException {
        String key = z + "/" + x + "/" + y;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        byte[] raw = reader.getTile(z, x, y);
        if (raw == null || raw.length == 0) {
            cache.put(key, null);
            return null;
        }

        try {
            var tile = org.maplibre.mlt.decoder.MltDecoder.decodeMlTile(raw);
            cache.put(key, tile);
            return tile;
        } catch (IOException e) {
            throw new ValidationError(
                "Failed to decode tile z=%d x=%d y=%d: %s".formatted(z, x, y, e.getMessage()),
                e
            );
        }
    }

    /**
     * Checks that the given tile has the expected layer, that it contains at least
     * {@code minFeatureCount} features, and that one of those features matches the
     * expected label within the geographic bounding box.
     */
    private void assertTileContains(
        org.maplibre.mlt.data.MapLibreTile tile,
        KnownFeature known
    ) {
        String layerName = known.layer();

        var layer = tile.layers().stream()
            .filter(l -> l.name().equals(layerName))
            .findFirst()
            .orElse(null);

        if (layer == null) {
            throw new ValidationError(
                "Layer '%s' not found in tile".formatted(layerName)
            );
        }

        if (layer.features().size() < known.minFeatureCount()) {
            throw new ValidationError(
                "Layer '%s' has %d features (expected >= %d)".formatted(
                    layerName, layer.features().size(), known.minFeatureCount()
                )
            );
        }

        Envelope bounds = known.bounds();
        String key = known.searchKey();
        String expectedValue = known.searchValue();

        var matching = layer.features().stream()
            .filter(f -> {
                Object val = f.properties().get(key);
                return val != null && val.toString().equals(expectedValue);
            })
            .filter(f -> {
                var geom = f.geometry();
                if (geom == null) return false;
                return bounds.contains(geom.getEnvelopeInternal());
            })
            .toList();

        if (matching.isEmpty()) {
            throw new ValidationError(
                "Feature '%s'=%s not found in layer '%s' within bounds %s".formatted(
                    key, expectedValue, layerName, bounds
                )
            );
        }
    }
}
