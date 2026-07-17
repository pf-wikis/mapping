package io.github.pfwikis.tiletest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import com.onthegomap.planetiler.VectorTile;

import io.github.pfwikis.layercompiler.steps.validation.TileFetcher;
import io.github.pfwikis.layercompiler.steps.validation.TileValidationException;
import io.github.pfwikis.layercompiler.steps.validation.TileValidator;

/**
 * Lightweight, static facade for writing tile-validation tests.
 *
 * <p>Mirrors the style the test-writer requested:
 * <pre>
 *   try (var t = TileAssertions.open("../frontend/public/golarion.pmtiles")) {
 *       t.tile(5, 16, 11)
 *        .layer("locations")
 *        .assertFeatureCountMin(1)
 *        .assertFeatureNear(30.89, -0.23, 150.0)
 *        .assertProperty("label", "Absalom");
 *   }
 * </pre>
 */
public class TileAssertions implements AutoCloseable {

    private final TileValidator validator;

    private TileAssertions(Path path) throws IOException {
        this.validator = TileValidator.open(path);
    }

    /** Open a PMTiles archive. */
    public static TileAssertions open(String path) throws IOException {
        return new TileAssertions(Path.of(path));
    }

    /** Open a PMTiles archive. */
    public static TileAssertions open(Path path) throws IOException {
        return new TileAssertions(path);
    }

    /**
     * Start a query for the given tile (XYZ scheme).
     * Returns an object on which you can select a layer and run assertions.
     */
    public TileQuery tile(int z, int x, int y) {
        return new TileQuery(this, z, x, y);
    }

    /**
     * Configure the default zoom level used by convenience/lat-lon overloads.
     * Defaults to 5.
     */
    public TileAssertions withDefaultZoom(int zoom) {
        validator.withDefaultZoom(zoom);
        return this;
    }

    /* ------------------------------------------------------------------ */

    @Override
    public void close() throws Exception {
        validator.close();
    }

    /**
     * Represents a single tile + layer combination under test.
     */
    public static class TileQuery {

        private final TileAssertions parent;
        private final int z, x, y;
        private String layerName;

        private TileQuery(TileAssertions parent, int z, int x, int y) {
            this.parent = parent;
            this.z = z;
            this.x = x;
            this.y = y;
        }

        /** Narrow the query to a named layer. */
        public TileQuery layer(String name) {
            this.layerName = name;
            return this;
        }

        /** Assert that the layer exists in this tile. */
        public TileQuery assertLayerExists() {
            parent.validator.assertLayerExists(layerName);
            return this;
        }

        /** Assert exact feature count for this layer. */
        public TileQuery assertFeatureCount(int expected) {
            parent.validator.assertFeatureCount(layerName, z, x, y, expected, expected);
            return this;
        }

        /** Assert feature count is at least the given number. */
        public TileQuery assertFeatureCountMin(int min) {
            parent.validator.assertFeatureCount(layerName, z, x, y, min, Integer.MAX_VALUE);
            return this;
        }

        /** Assert feature count falls in a range. */
        public TileQuery assertFeatureCount(int min, int max) {
            parent.validator.assertFeatureCount(layerName, z, x, y, min, max);
            return this;
        }

        /** Assert at least one feature in this layer is near the lat/lon point. */
        public TileQuery assertFeatureNear(double lat, double lon, double toleranceKm) {
            // Fetch the features for the specific tile, not the default-zoom sampling
            var features = parent.validator.layer(z, x, y, layerName);
            if (features.isEmpty()) {
                throw TileValidationException.fail(
                    "Layer '" + layerName + "' is empty at %d/%d/%d; cannot find feature near %.4f,%.4f"
                        .formatted(z, x, y, lat, lon));
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
                        .formatted(z, x, y, toleranceKm, lat, lon));
            }
            return this;
        }

        /** Assert at least one feature in this layer has the exact property value. */
        public TileQuery assertProperty(String key, Object value) {
            var features = parent.validator.layer(z, x, y, layerName);
            if (features.isEmpty()) {
                throw TileValidationException.fail(
                    "Layer '" + layerName + "' is empty at %d/%d/%d; cannot assert property"
                        .formatted(z, x, y));
            }
            boolean found = features.stream().anyMatch(f ->
                Objects.equals(f.tags().get(key), value));
            if (!found) {
                throw TileValidationException.fail(
                    "Layer '" + layerName + "' has no feature with '" + key + "'=" + value +
                        " at %d/%d/%d".formatted(z, x, y));
            }
            return this;
        }

        /** Assert at least one feature in this layer has a property containing the given substring. */
        public TileQuery assertPropertyContains(String key, String substring) {
            var features = parent.validator.layer(z, x, y, layerName);
            if (features.isEmpty()) {
                throw TileValidationException.fail(
                    "Layer '" + layerName + "' is empty at %d/%d/%d; cannot assert property"
                        .formatted(z, x, y));
            }
            boolean found = features.stream().anyMatch(f -> {
                Object val = f.tags().get(key);
                return val != null && val.toString().contains(substring);
            });
            if (!found) {
                throw TileValidationException.fail(
                    "Layer '" + layerName + "' has no feature with '" + key + "' containing '" + substring + "'"
                        + " at %d/%d/%d".formatted(z, x, y));
            }
            return this;
        }

        /** Decode a vector-tile feature to JTS (re-scales to world coordinates). */
        private Geometry decodeGeometry(VectorTile.Feature f) {
            try {
                return f.geometry().decode();
            } catch (Exception e) {
                return null;
            }
        }

        /** Haversine distance from a JTS geometry centroid to a lat/lon point (km). */
        private double distanceKm(Geometry geom, double lat, double lon) {
            Point centroid = geom.getCentroid();
            return haversine(centroid.getY(), centroid.getX(), lat, lon);
        }

        private static double haversine(double lat1, double lon1, double lat2, double lon2) {
            final double R = 6371.0;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                     + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                     * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return R * c;
        }
    }
}
