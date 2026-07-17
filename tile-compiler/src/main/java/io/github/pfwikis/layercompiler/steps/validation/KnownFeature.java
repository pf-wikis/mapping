package io.github.pfwikis.layercompiler.steps.validation;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A pre-defined assertion about a specific tile that should contain a known feature.
 * Used by validation rules that check real-world content correctness.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class KnownFeature {

    /** A human-readable label used in error messages. */
    private final String name;

    /** Expected layer in the vector tile. */
    private final String layer;

    /** The property key used to look up the feature (e.g. "label" for locations). */
    private final String searchKey;

    /** The expected property value. */
    private final String searchValue;

    /** Expected geographic location (lat/lon, as stored in the source GeoJSON). */
    private final double lat, lon;

    /** How many kilometres the tile coordinate may deviate from the expected spot (rough). */
    private final double toleranceKm;

    /** Zoom level to sample. */
    private final int zoom;

    /** Tile coordinate tolerance at the given zoom. A value of 2 means any tile within
     *  a 5×5 neighbourhood of the computed tile (x±2, y±2) is acceptable. */
    private final int tileTolerance;

    /** Minimum feature count expected in the matched tile for the given layer. */
    private final int minFeatureCount;

    /**
     * Computes the X tile coordinate for this zoom. Uses the standard Web Mercator formula.
     */
    public int tileX() {
        return (int) Math.floor(
            (lon + 180.0) / 360.0 * (1 << zoom)
        );
    }

    /**
     * Computes the Y tile coordinate for this zoom. Uses the standard Web Mercator formula.
     */
    public int tileY() {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor(
            (1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom)
        );
    }

    /**
     * Returns an envelope (in lat/lon) that expands the expected point by {@code toleranceKm}
     * on every side. Used for a quick bounding-box sanity check before converting to tile
     * coordinates.
     */
    public Envelope bounds() {
        // Approx. 1° lat  ≈ 111 km; 1° lon ≈ 111 km * cos(lat)
        double kmToDegLat = 1.0 / 111.0;
        double kmToDegLon = 1.0 / (111.0 * Math.cos(Math.toRadians(lat)));
        return new Envelope(
            lon - toleranceKm * kmToDegLon,
            lon + toleranceKm * kmToDegLon,
            lat - toleranceKm * kmToDegLat,
            lat + toleranceKm * kmToDegLat
        );
    }
}
