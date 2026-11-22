/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.jcip.annotations.Immutable
 *  org.locationtech.jts.geom.Coordinate
 *  org.locationtech.jts.geom.CoordinateXY
 *  org.locationtech.jts.geom.Envelope
 */
package io.github.pfwikis.tile;




import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;

public record TileCoord(long encoded, int x, int y, int z) implements Comparable<TileCoord>
{
    private static final long[] ZOOM_START_INDEX = new long[17];

    public TileCoord {
        assert (z <= 16);
    }

    private static long startIndexForZoom(int z) {
        return ZOOM_START_INDEX[z];
    }

    private static int zoomForIndex(long idx) {
        for (int z = 16; z >= 0; --z) {
            if (ZOOM_START_INDEX[z] > idx) continue;
            return z;
        }
        throw new IllegalArgumentException("Bad index: " + idx);
    }

    public static TileCoord ofXYZ(int x, int y, int z) {
        return new TileCoord(TileCoord.encode(x, y, z), x, y, z);
    }

    public static TileCoord decode(long encoded) {
        int z = TileCoord.zoomForIndex(encoded);
        long xy = TileCoord.tmsPositionToXY(z, encoded - TileCoord.startIndexForZoom(z));
        return new TileCoord(encoded, (int)(xy >>> 32 & 0xFFFFFFFFL), (int)(xy & 0xFFFFFFFFL), z);
    }

    public static TileCoord hilbertDecode(long encoded) {
        int z = TileCoord.zoomForIndex(encoded);
        long xy = Hilbert.hilbertPositionToXY(z, encoded - TileCoord.startIndexForZoom(z));
        return TileCoord.ofXYZ(Hilbert.extractX(xy), Hilbert.extractY(xy), z);
    }

    public static TileCoord aroundLngLat(double lng, double lat, int zoom) {
        double factor = 1 << zoom;
        double x = GeoUtils.getWorldX(lng) * factor;
        double y = GeoUtils.getWorldY(lat) * factor;
        return TileCoord.ofXYZ((int)Math.floor(x), (int)Math.floor(y), zoom);
    }

    public static long encode(int x, int y, int z) {
        return TileCoord.startIndexForZoom(z) + TileCoord.tmsXYToPosition(z, x, y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        TileCoord tileCoord = (TileCoord)o;
        return this.encoded == tileCoord.encoded;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.encoded);
    }

    @Override
    public String toString() {
        return "{x=" + this.x + " y=" + this.y + " z=" + this.z + "}";
    }

    public double progressOnLevel(TileExtents extents) {
        TileExtents.ForZoom zoomBounds = extents.getForZoom(this.z);
        return 1.0 * (double)(this.x - zoomBounds.minX()) / (double)(zoomBounds.maxX() - zoomBounds.minX());
    }

    public double hilbertProgressOnLevel(TileExtents extents) {
        return 1.0 * (double)Hilbert.hilbertXYToIndex(this.z, this.x, this.y) / (double)(1L << 2 * this.z);
    }

    @Override
    public int compareTo(TileCoord o) {
        return Long.compare(this.encoded, o.encoded);
    }

    public Envelope getEnvelope() {
        double worldWidthAtZoom = Math.pow(2.0, this.z);
        return new Envelope(GeoUtils.getWorldLon((double)this.x / worldWidthAtZoom), GeoUtils.getWorldLon((double)(this.x + 1) / worldWidthAtZoom), GeoUtils.getWorldLat((double)(this.y + 1) / worldWidthAtZoom), GeoUtils.getWorldLat((double)this.y / worldWidthAtZoom));
    }

    public String getDebugUrl(String pattern) {
        Coordinate center = this.getEnvelope().centre();
        DecimalFormat format = new DecimalFormat("0.#####", DecimalFormatSymbols.getInstance(Locale.US));
        return pattern.replaceAll("\\{(lat|latitude)}", format.format(center.y)).replaceAll("\\{(lon|longitude)}", format.format(center.x)).replaceAll("\\{(z|zoom)}", this.z + ".5");
    }

    public Coordinate lngLatToTileCoords(double lng, double lat) {
        double factor = 1 << this.z;
        double x = GeoUtils.getWorldX(lng) * factor;
        double y = GeoUtils.getWorldY(lat) * factor;
        return new CoordinateXY((x - Math.floor(x)) * 256.0, (y - Math.floor(y)) * 256.0);
    }

    public long hilbertEncoded() {
        return TileCoord.hilbertEncoded(this.x, this.y, this.z);
    }

    public static long hilbertEncoded(int x, int y, int z) {
        return TileCoord.startIndexForZoom(z) + Hilbert.hilbertXYToIndex(z, x, y);
    }

    /**
     * First encoded id of the aligned Hilbert block containing {@code (x,y)}.
     * The alignment has to be done in local zoom space: the per-zoom id offset
     * is not a multiple of the block size.
     */
    public static long hilbertBlockStart(int x, int y, int z, long blockSize) {
        long local = Hilbert.hilbertXYToIndex(z, x, y);
        return TileCoord.startIndexForZoom(z) + (local & ~(blockSize - 1L));
    }

    public static long tmsPositionToXY(int z, long pos) {
        if (z == 0) {
            return 0L;
        }
        long dim = 1L << z;
        long x = pos / dim;
        long y = dim - 1L - pos % dim;
        return x << 32 | y;
    }

    public static long tmsXYToPosition(int z, int x, int y) {
        long dim = 1L << z;
        return (long)x * dim + (dim - 1L - (long)y);
    }

    public TileCoord parent() {
        return TileCoord.ofXYZ(this.x / 2, this.y / 2, this.z - 1);
    }

    public Envelope bounds() {
        double worldWidthAtZoom = Math.pow(2.0, this.z);
        return new Envelope(GeoUtils.getWorldLon((double)this.x / worldWidthAtZoom), GeoUtils.getWorldLon((double)(this.x + 1) / worldWidthAtZoom), GeoUtils.getWorldLat((double)this.y / worldWidthAtZoom), GeoUtils.getWorldLat((double)(this.y + 1) / worldWidthAtZoom));
    }

    static {
        long idx = 0L;
        for (int z = 0; z <= 16; ++z) {
            TileCoord.ZOOM_START_INDEX[z] = idx;
            long count = 1L << 2 * z;
            idx += count;
        }
    }
}

