package io.github.pfwikis.layercompiler.steps.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.TileCoord;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides fluent, assertion-style helpers for validating the contents of a PMTiles archive.
 *
 * <p>Usage:
 * <pre>
 *   try (var v = TileValidator.open(path)) {
 *       v.assertLayerExists("land");
 *       v.assertFeatureCount("land", 1, 100);
 *       v.assertFeatureNear("land", 25.0, 30.0, 50.0);
 *   }
 * </pre>
 */
@Slf4j
public class TileValidator implements AutoCloseable {

    private final TileFetcher fetcher;
    private int defaultZoom = 5;

    private TileValidator(Path path) throws IOException {
        this.fetcher = new TileFetcher(path);
    }

    /** Open a PMTiles file for validation. */
    public static TileValidator open(Path path) throws IOException {
        return new TileValidator(path);
    }

    /** Set the zoom level that implicit tile lookups should use. */
    public TileValidator withDefaultZoom(int zoom) {
        this.defaultZoom = zoom;
        return this;
    }

    /** --- helpers ---------------------------------------------------------- */

    /** Decode a single tile (XYZ scheme). */
    public List<VectorTile.Feature> tile(int z, int x, int y) {
        return fetcher.decodeTile(z, x, y);
    }

    /** Decode only the features in {@code layerName} for the given tile. */
    public List<VectorTile.Feature> layer(int z, int x, int y, String layerName) {
        return fetcher.decodeLayer(z, x, y, layerName);
    }

    /** Convenience: resolve lat/lon to the appropriate tile and return its layer. */
    public List<VectorTile.Feature> layerForLatLon(double lat, double lon, String layerName) {
        TileCoord tc = TileFetcher.tileForLatLon(lat, lon, defaultZoom);
        return layer(tc.z(), tc.x(), tc.y(), layerName);
    }

    /** Convenience: resolve lat/lon to the appropriate tile and return all features. */
    public List<VectorTile.Feature> tileForLatLon(double lat, double lon) {
        TileCoord tc = TileFetcher.tileForLatLon(lat, lon, defaultZoom);
        return tile(tc.z(), tc.x(), tc.y());
    }

    /* --------------------------------------------------------------------- */
    /*  Assertion helpers                                                    */
    /* --------------------------------------------------------------------- */

    /**
     * Assert that {@code layerName} appears in at least one tile that covers
     * the default zoom level.
     */
    public TileValidator assertLayerExists(String layerName) {
        // sample a few tiles around the world to find the layer
        boolean found = sampleTiles(defaultZoom, (z, x, y) -> {
            var f = fetcher.decodeLayer(z, x, y, layerName);
            return !f.isEmpty();
        });
        if (!found) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' not found in any sampled tile at zoom " + defaultZoom);
        }
        log.debug("assertLayerExists('{}') passed", layerName);
        return this;
    }

    /**
     * Assert that the feature count for {@code layerName} at a given tile
     * falls inside {@code [min, max]} (inclusive).
     *
     * <p>Use {@code max = Integer.MAX_VALUE} for an unbounded upper limit.
     */
    public TileValidator assertFeatureCount(String layerName, int z, int x, int y, int min, int max) {
        var features = layer(z, x, y, layerName);
        int count = features.size();
        if (count < min || count > max) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' at %d/%d/%d has %d features, expected between %d and %d"
                    .formatted(z, x, y, count, min, max));
        }
        log.debug("assertFeatureCount('{}',{}/{}/{},{},{}) passed ({})",
            layerName, z, x, y, min, max, count);
        return this;
    }

    /** Convenience overload using the default zoom. */
    public TileValidator assertFeatureCount(String layerName, int min, int max) {
        boolean checked = sampleTiles(defaultZoom, (z, x, y) -> {
            var features = layer(z, x, y, layerName);
            int count = features.size();
            return count >= min && count <= max;
        });
        if (!checked) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' did not have a feature count in [%d, %d] in any sampled tile at zoom %d"
                    .formatted(min, max, defaultZoom));
        }
        log.debug("assertFeatureCount('{}',{},{}) passed", layerName, min, max);
        return this;
    }

    /**
     * Assert that at least one feature in the given layer (at default zoom) has
     * the specified property key set to the expected value.
     *
     * @param expected may be {@code null} to assert the key is absent or null-valued.
     */
    public TileValidator assertFeatureProperty(String layerName, String key, Object expected) {
        var features = layerForLatLon(0, 0, layerName); // 0,0 is a fallback; caller may prefer an explicit tile
        if (features.isEmpty()) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' is empty at default zoom, cannot assert property '" + key + "'");
        }

        boolean found = features.stream().anyMatch(f -> {
            Object actual = f.tags().get(key);
            return Objects.equals(actual, expected);
        });

        if (!found) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' has no feature with '" + key + "'=" + expected +
                    " (sampled %d features)".formatted(features.size()));
        }
        log.debug("assertFeatureProperty('{}','{}','{}') passed", layerName, key, expected);
        return this;
    }

    /**
     * Assert that at least one feature in the given layer exists inside a
     * distance of {@code toleranceKm} from {@code lat}/{@code lon}.
     *
     * <p>Uses JTS geometry decoding for high-fidelity comparison.  Earth curvature
     * is approximated with the haversine formula for the distance threshold.
     */
    public TileValidator assertFeatureNear(String layerName, double lat, double lon, double toleranceKm) {
        TileCoord tc = TileFetcher.tileForLatLon(lat, lon, defaultZoom);
        var features = layer(tc.z(), tc.x(), tc.y(), layerName);

        if (features.isEmpty()) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' is empty at %d/%d/%d; cannot find feature near %.4f,%.4f"
                    .formatted(tc.z(), tc.x(), tc.y(), lat, lon));
        }

        boolean found = false;
        for (VectorTile.Feature f : features) {
            Geometry geom = decodeGeometry(f);
            if (geom != null && distanceKm(geom, lat, lon) <= toleranceKm) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' at %d/%d/%d has no feature within %.2f km of %.4f,%.4f"
                    .formatted(tc.z(), tc.x(), tc.y(), toleranceKm, lat, lon));
        }
        log.debug("assertFeatureNear('{}',{},{},{}) passed", layerName, lat, lon, toleranceKm);
        return this;
    }

    /**
     * Assert that at least one feature in {@code layerName} has a decoded
     * geometry that geometrically overlaps {@code expected} within {@code toleranceKm}.
     *
     * <p>This is useful for verifying that a coastline, border, or river roughly
     * matches a known reference geometry.
     */
    public TileValidator assertGeometryRoughlyMatches(
            String layerName, int z, int x, int y,
            Geometry expected, double toleranceKm) {

        List<VectorTile.Feature> features = layer(z, x, y, layerName);
        if (features.isEmpty()) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' is empty at %d/%d/%d".formatted(z, x, y));
        }

        Envelope env = expected.getEnvelopeInternal();
        // Quick haversine approximation for envelope tolerance in degrees
        double latTol = toleranceKm / 111.0;
        double lonTol = toleranceKm / (111.0 * Math.cos(Math.toRadians(env.centre().y)));
        Envelope relaxedEnv = new Envelope(
            env.getMinX() - lonTol, env.getMaxX() + lonTol,
            env.getMinY() - latTol, env.getMaxY() + latTol);

        List<Geometry> candidates = new ArrayList<>();
        for (VectorTile.Feature f : features) {
            Geometry g = decodeGeometry(f);
            if (g == null) continue;
            if (!relaxedEnv.contains(g.getEnvelopeInternal())) continue;
            candidates.add(g);
        }

        if (candidates.isEmpty()) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' at %d/%d/%d has no geometry inside the relaxed envelope"
                    .formatted(z, x, y));
        }

        // Simple heuristic: at least one candidate centroid is close to expected centroid
        Point expectedCentroid = expected.getCentroid();
        boolean match = candidates.stream().anyMatch(g ->
            distanceKm(g.getCentroid(), expectedCentroid.getY(), expectedCentroid.getX()) <= toleranceKm);

        if (!match) {
            throw TileValidationException.fail(
                "Layer '" + layerName + "' at %d/%d/%d: no geometry matches within %.2f km of expected centroid"
                    .formatted(z, x, y, toleranceKm));
        }
        log.debug("assertGeometryRoughlyMatches('{}',{}/{}/{}) passed", layerName, z, x, y);
        return this;
    }

    /** Convenience overload asserting at default zoom from lat/lon tile. */
    public TileValidator assertGeometryRoughlyMatches(
            String layerName, double lat, double lon, Geometry expected, double toleranceKm) {
        TileCoord tc = TileFetcher.tileForLatLon(lat, lon, defaultZoom);
        return assertGeometryRoughlyMatches(layerName, tc.z(), tc.x(), tc.y(), expected, toleranceKm);
    }

    /* --------------------------------------------------------------------- */
    /*  Internal helpers                                                     */
    /* --------------------------------------------------------------------- */

    /** Decode a vector-tile feature to JTS (re-scales to world coordinates). */
    private Geometry decodeGeometry(VectorTile.Feature f) {
        try {
            return f.geometry().decode();
        } catch (Exception e) {
            log.warn("Could not decode geometry for feature in layer '{}'", f.layer(), e);
            return null;
        }
    }

    /** Sample tiles in a small grid at the given zoom. */
    private boolean sampleTiles(int zoom, TileSampler sampler) {
        int max = (1 << zoom) - 1;
        // sample corners and center-ish tiles
        int[] xs = {0, max / 2, max};
        int[] ys = {0, max / 2, max};
        for (int x : xs) {
            for (int y : ys) {
                if (sampler.test(zoom, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface TileSampler {
        boolean test(int z, int x, int y);
    }

    /** Haversine distance from a JTS geometry to a lat/lon point (km). */
    private double distanceKm(Geometry geom, double lat, double lon) {
        // Fast-path: centroid distance
        Point centroid = geom.getCentroid();
        return haversine(centroid.getY(), centroid.getX(), lat, lon);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius, km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    public void close() throws IOException {
        fetcher.close();
    }
}
