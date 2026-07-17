package io.github.pfwikis.layercompiler.steps.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.archive.ReadableTileArchive;
import com.onthegomap.planetiler.geo.TileCoord;
import com.onthegomap.planetiler.pmtiles.ReadablePmtiles;

import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around ReadablePmtiles that provides decoded tile lookups
 * with JTS-coordinate convenience.
 */
@Slf4j
public class TileFetcher implements AutoCloseable {

    private final ReadableTileArchive pmtiles;

    public TileFetcher(Path path) throws IOException {
        this.pmtiles = ReadablePmtiles.newReadFromFile(path);
        log.debug("Opened PMTiles: minZoom={}, maxZoom={}",
            ((ReadablePmtiles) this.pmtiles).getHeader().minZoom(),
            ((ReadablePmtiles) this.pmtiles).getHeader().maxZoom());
    }

    /** Fetch raw bytes for a tile; returns {@code null} if the tile is missing. */
    public byte[] getTileBytes(int z, int x, int y) {
        // PMTiles uses TMS scheme; y-flip for XYZ scheme
        int tmsY = (1 << z) - 1 - y;
        return pmtiles.getTile(z, x, tmsY);
    }

    /** Decode a tile into its constituent vector-tile features. Empty if tile absent. */
    public List<VectorTile.Feature> decodeTile(int z, int x, int y) {
        byte[] raw = getTileBytes(z, x, y);
        if (raw == null || raw.length == 0) {
            return List.of();
        }
        return VectorTile.decode(raw);
    }

    /** Decode a tile and return only features belonging to {@code layerName}. */
    public List<VectorTile.Feature> decodeLayer(int z, int x, int y, String layerName) {
        return decodeTile(z, x, y).stream()
            .filter(f -> layerName.equals(f.layer()))
            .toList();
    }

    /**
     * Convert lat/lon to the tile XYZ coordinates at a given zoom.
     * Handy for tests that want to say “zoom 5 tile that contains Absalom”.
     */
    public static TileCoord tileForLatLon(double lat, double lon, int zoom) {
        return TileCoord.aroundLngLat(lon, lat, zoom);
    }

    /**
     * Convert a lat/lon point into tile-local pixel coordinates (0..256 at the
     * given zoom).  Useful for exact assertions inside a single tile.
     */
    public static Coordinate latLonToTileCoords(TileCoord tile, double lat, double lon) {
        return tile.lngLatToTileCoords(lon, lat);
    }

    @Override
    public void close() throws IOException {
        pmtiles.close();
    }
}
