/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.carrotsearch.hppc.IntObjectHashMap
 *  com.carrotsearch.hppc.IntObjectMap
 *  com.carrotsearch.hppc.cursors.IntCursor
 *  com.carrotsearch.hppc.cursors.IntObjectCursor
 *  net.jcip.annotations.NotThreadSafe
 *  org.locationtech.jts.geom.Coordinate
 *  org.locationtech.jts.geom.CoordinateSequence
 *  org.locationtech.jts.geom.Geometry
 *  org.locationtech.jts.geom.GeometryCollection
 *  org.locationtech.jts.geom.LineString
 *  org.locationtech.jts.geom.Lineal
 *  org.locationtech.jts.geom.MultiLineString
 *  org.locationtech.jts.geom.MultiPoint
 *  org.locationtech.jts.geom.MultiPolygon
 *  org.locationtech.jts.geom.Point
 *  org.locationtech.jts.geom.Polygon
 *  org.locationtech.jts.geom.Polygonal
 *  org.locationtech.jts.geom.Puntal
 *  org.locationtech.jts.geom.impl.PackedCoordinateSequence$Double
 *  org.roaringbitmap.PeekableIntIterator
 *  org.roaringbitmap.RoaringBitmap
 */
package io.github.pfwikis.tile;















import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.roaringbitmap.PeekableIntIterator;
import org.roaringbitmap.RoaringBitmap;

public class TiledGeometry {
    private static final Format FORMAT = Format.defaultInstance();
    private static final double NEIGHBOR_BUFFER_EPS = 2.44140625E-5;
    private final Map<TileCoord, List<List<CoordinateSequence>>> tileContents = new HashMap<TileCoord, List<List<CoordinateSequence>>>();
    private final TileExtents.ForZoom extents;
    private final double buffer;
    private final double neighborBuffer;
    private final int z;
    private final boolean area;
    private final int maxTilesAtThisZoom;
    private Map<Integer, IntRangeSet> filledRanges = null;

    private TiledGeometry(TileExtents.ForZoom extents, double buffer, int z, boolean area) {
        this.extents = extents;
        this.buffer = buffer;
        this.neighborBuffer = buffer + 2.44140625E-5;
        this.z = z;
        this.area = area;
        this.maxTilesAtThisZoom = 1 << z;
    }

    static TiledGeometry slicePointsIntoTiles(TileExtents.ForZoom extents, double buffer, int z, Coordinate[] coords) {
        TiledGeometry result = new TiledGeometry(extents, buffer, z, false);
        for (Coordinate coord : coords) {
            result.slicePoint(coord);
        }
        return result;
    }

    private static int wrapInt(int value, int max) {
        if ((value %= max) < 0) {
            value += max;
        }
        return value;
    }

    public static TiledGeometry sliceIntoTiles(Geometry scaledGeom, double minSize, double buffer, int z, TileExtents.ForZoom extents) throws GeometryException {
        if (scaledGeom.isEmpty()) {
            return new TiledGeometry(extents, buffer, z, false);
        }
        if (scaledGeom instanceof Point) {
            Point point = (Point)scaledGeom;
            return TiledGeometry.slicePointsIntoTiles(extents, buffer, z, point.getCoordinates());
        }
        if (scaledGeom instanceof MultiPoint) {
            MultiPoint points = (MultiPoint)scaledGeom;
            return TiledGeometry.slicePointsIntoTiles(extents, buffer, z, points.getCoordinates());
        }
        if (scaledGeom instanceof Polygon || scaledGeom instanceof MultiPolygon || scaledGeom instanceof LineString || scaledGeom instanceof MultiLineString) {
            List<List<CoordinateSequence>> coordinateSequences = GeometryCoordinateSequences.extractGroups(scaledGeom, minSize);
            boolean area = scaledGeom instanceof Polygonal;
            return TiledGeometry.sliceIntoTiles(coordinateSequences, buffer, area, z, extents);
        }
        throw new UnsupportedOperationException("Unsupported JTS geometry type " + scaledGeom.getClass().getSimpleName() + " " + scaledGeom.getGeometryType());
    }

    public static CoveredTiles getCoveredTiles(Geometry scaledGeom, int zoom, TileExtents.ForZoom extents) throws GeometryException {
        if (scaledGeom.isEmpty()) {
            return new CoveredTiles(new RoaringBitmap(), zoom);
        }
        if (scaledGeom instanceof Puntal || scaledGeom instanceof Polygonal || scaledGeom instanceof Lineal) {
            return TiledGeometry.sliceIntoTiles(scaledGeom, 0.0, 0.0, zoom, extents).getCoveredTiles();
        }
        if (scaledGeom instanceof GeometryCollection) {
            GeometryCollection gc = (GeometryCollection)scaledGeom;
            CoveredTiles result = new CoveredTiles(new RoaringBitmap(), zoom);
            for (int i = 0; i < gc.getNumGeometries(); ++i) {
                result = CoveredTiles.merge(TiledGeometry.getCoveredTiles(gc.getGeometryN(i), zoom, extents), result);
            }
            return result;
        }
        throw new UnsupportedOperationException("Unsupported JTS geometry type " + scaledGeom.getClass().getSimpleName() + " " + scaledGeom.getGeometryType());
    }

    static TiledGeometry sliceIntoTiles(List<List<CoordinateSequence>> groups, double buffer, boolean area, int z, TileExtents.ForZoom extents) throws GeometryException {
        TiledGeometry result = new TiledGeometry(extents, buffer, z, area);
        EnumSet<Direction> wrapResult = result.sliceWorldCopy(groups, 0);
        if (wrapResult.contains((Object)Direction.RIGHT)) {
            result.sliceWorldCopy(groups, -result.maxTilesAtThisZoom);
        }
        if (wrapResult.contains((Object)Direction.LEFT)) {
            result.sliceWorldCopy(groups, result.maxTilesAtThisZoom);
        }
        return result;
    }

    private static int wrapX(int x, int max) {
        if ((x %= max) < 0) {
            x += max;
        }
        return x;
    }

    private static void intersectX(MutableCoordinateSequence out, double ax, double ay, double bx, double by, double x) {
        double t = (x - ax) / (bx - ax);
        out.addPoint(x, ay + (by - ay) * t);
    }

    private static void intersectY(MutableCoordinateSequence out, double ax, double ay, double bx, double by, double y) {
        double t = (y - ay) / (by - ay);
        out.addPoint(ax + (bx - ax) * t, y);
    }

    private static CoordinateSequence fill(double buffer) {
        double min = -256.0 * buffer;
        double max = 256.0 - min;
        return new PackedCoordinateSequence.Double(new double[]{min, min, max, min, max, max, min, max, min, min}, 2, 0);
    }

    private void slicePoint(Coordinate coord) {
        double worldX = coord.getX();
        double worldY = coord.getY();
        int minX = (int)Math.floor(worldX - this.neighborBuffer);
        int maxX = (int)Math.floor(worldX + this.neighborBuffer);
        int minY = Math.max(this.extents.minY(), (int)Math.floor(worldY - this.neighborBuffer));
        int maxY = Math.min(this.extents.maxY() - 1, (int)Math.floor(worldY + this.neighborBuffer));
        for (int x = minX; x <= maxX; ++x) {
            double tileX = worldX - (double)x;
            int wrappedX = TiledGeometry.wrapInt(x, this.maxTilesAtThisZoom);
            if (!this.extents.testX(wrappedX)) continue;
            for (int y = minY; y <= maxY; ++y) {
                if (!this.extents.test(wrappedX, y)) continue;
                TileCoord tile = TileCoord.ofXYZ(wrappedX, y, this.z);
                double tileY = worldY - (double)y;
                ((List)this.tileContents.computeIfAbsent(tile, t -> List.of(new ArrayList())).getFirst()).add(GeoUtils.coordinateSequence(tileX * 256.0, tileY * 256.0));
            }
        }
    }

    public int zoomLevel() {
        return this.z;
    }

    public Iterable<TileCoord> getFilledTiles() {
        return this.filledRanges == null ? Collections.emptyList() : () -> this.filledRanges.entrySet().stream().<TileCoord>mapMulti((entry, next) -> {
            int x = (Integer)entry.getKey();
            PrimitiveIterator.OfInt ofInt = ((IntRangeSet)entry.getValue()).iterator();
            while (ofInt.hasNext()) {
                TileCoord coord;
                int y = (Integer)ofInt.next();
                if (!this.extents.test(x, y) || this.tileContents.containsKey(coord = TileCoord.ofXYZ(x, y, this.z))) continue;
                next.accept(coord);
            }
        }).iterator();
    }

    /** Filled y-ranges keyed by tile column, or {@code null} if no tile is fully covered. */
    public Map<Integer, IntRangeSet> filledRanges() {
        return this.filledRanges;
    }

    /** Boundary tiles as y-ranges per tile column, used to suppress fills there. */
    public Map<Integer, IntRangeSet> boundaryRanges() {
        var result = new HashMap<Integer, IntRangeSet>(this.tileContents.size());
        for(var tile : this.tileContents.keySet()) {
            result.computeIfAbsent(tile.x(), x -> new IntRangeSet()).add(tile.y(), tile.y());
        }
        return result;
    }

    /** Hilbert ids of tiles that already have boundary geometry and must not also get a fill. */
    public com.carrotsearch.hppc.LongHashSet boundaryTileIds() {
        var boundaries = new com.carrotsearch.hppc.LongHashSet(this.tileContents.size());
        for(var tile : this.tileContents.keySet())
            boundaries.add(tile.hilbertEncoded());
        return boundaries;
    }

    /**
     * Iterates the ids of tiles completely covered by this geometry without
     * allocating a {@link TileCoord} per tile. Used to stream fill features.
     */
    public void forEachFilledTile(java.util.function.LongConsumer consumer) {
        if(this.filledRanges == null)
            return;
        var boundaries = boundaryTileIds();
        for(var entry : this.filledRanges.entrySet()) {
            int x = entry.getKey();
            if(!this.extents.testX(x))
                continue;
            var ys = entry.getValue().iterator();
            while(ys.hasNext()) {
                int y = ys.nextInt();
                if(!this.extents.testY(y))
                    continue;
                long id = TileCoord.hilbertEncoded(x, y, this.z);
                if(!boundaries.contains(id))
                    consumer.accept(id);
            }
        }
    }

    public CoveredTiles getCoveredTiles() {
        RoaringBitmap bitmap = new RoaringBitmap();
        for (TileCoord tileCoord : this.tileContents.keySet()) {
            bitmap.add(TiledGeometry.encode(this.maxTilesAtThisZoom, tileCoord.x(), tileCoord.y()));
        }
        if (this.filledRanges != null) {
            for (Map.Entry entry : this.filledRanges.entrySet()) {
                long colStart = TiledGeometry.encode(this.maxTilesAtThisZoom, ((Integer)entry.getKey()).intValue(), 0L);
                IntRangeSet yRanges = (IntRangeSet)entry.getValue();
                bitmap.or(RoaringBitmap.addOffset((RoaringBitmap)yRanges.bitmap(), (long)colStart));
            }
        }
        return new CoveredTiles(bitmap, this.z);
    }

    public Map<TileCoord, List<List<CoordinateSequence>>> getTileData() {
        return this.tileContents;
    }

    private EnumSet<Direction> sliceWorldCopy(List<List<CoordinateSequence>> groups, int xOffset) throws GeometryException {
        EnumSet<Direction> overflow = EnumSet.noneOf(Direction.class);
        for (List<CoordinateSequence> group : groups) {
            HashMap<TileCoord, List<CoordinateSequence>> inProgressShapes = new HashMap<TileCoord, List<CoordinateSequence>>();
            for (int i = 0; i < group.size(); ++i) {
                CoordinateSequence segment = group.get(i);
                boolean isOuterRing = i == 0;
                IntObjectMap<List<MutableCoordinateSequence>> xSlices = this.sliceX(segment);
                for (IntObjectCursor<List<MutableCoordinateSequence>> xCursor : xSlices) {
                    int x = xCursor.key + xOffset;
                    if (x >= this.maxTilesAtThisZoom) {
                        overflow.add(Direction.RIGHT);
                        continue;
                    }
                    if (x < 0) {
                        overflow.add(Direction.LEFT);
                        continue;
                    }
                    for (CoordinateSequence stripeSegment : xCursor.value) {
                        IntRangeSet filledYRange = this.sliceY(stripeSegment, x, isOuterRing, inProgressShapes);
                        if (!this.area || filledYRange == null) continue;
                        if (isOuterRing) {
                            this.addFilledRange(x, filledYRange);
                            continue;
                        }
                        this.removeFilledRange(x, filledYRange);
                    }
                }
            }
            this.addShapeToResults(inProgressShapes);
        }
        return overflow;
    }

    private void addShapeToResults(Map<TileCoord, List<CoordinateSequence>> inProgressShapes) {
        for (Map.Entry<TileCoord, List<CoordinateSequence>> entry : inProgressShapes.entrySet()) {
            int minPoints;
            TileCoord tileID = entry.getKey();
            List<CoordinateSequence> inSeqs = entry.getValue();
            if (this.area && inSeqs.getFirst().size() < 4) continue;
            int n = minPoints = this.area ? 4 : 2;
            List<CoordinateSequence> outSeqs = inSeqs.stream().filter(seq -> seq.size() >= minPoints).toList();
            if (outSeqs.isEmpty() || !this.extents.test(tileID.x(), tileID.y())) continue;
            this.tileContents.computeIfAbsent(tileID, tile -> new ArrayList()).add(outSeqs);
        }
    }

    private IntObjectMap<List<MutableCoordinateSequence>> sliceX(CoordinateSequence segment) {
        double leftLimit = -this.buffer;
        double rightLimit = 1.0 + this.buffer;
        IntObjectHashMap<List<MutableCoordinateSequence>> newGeoms = Hppc.newIntObjectHashMap();
        IntObjectHashMap<MutableCoordinateSequence> xSlices = Hppc.newIntObjectHashMap();
        int end = segment.size() - 1;
        for (int i = 0; i < end; ++i) {
            double ax = segment.getX(i);
            double ay = segment.getY(i);
            double bx = segment.getX(i + 1);
            double by = segment.getY(i + 1);
            double minX = Math.min(ax, bx);
            double maxX = Math.max(ax, bx);
            int startX = (int)Math.floor(minX - this.neighborBuffer);
            int endX = (int)Math.floor(maxX + this.neighborBuffer);
            for (int x2 = startX; x2 <= endX; ++x2) {
                double axTile = ax - (double)x2;
                double bxTile = bx - (double)x2;
                MutableCoordinateSequence slice = xSlices.get(x2);
                if (slice == null) {
                    slice = new MutableCoordinateSequence();
                    xSlices.put(x2, slice);
                    ArrayList<MutableCoordinateSequence> newGeom = (ArrayList<MutableCoordinateSequence>)newGeoms.get(x2);
                    if (newGeom == null) {
                        newGeom = new ArrayList<MutableCoordinateSequence>();
                        newGeoms.put(x2, newGeom);
                    }
                    newGeom.add(slice);
                }
                boolean exited = false;
                if (axTile < leftLimit) {
                    if (bxTile > leftLimit) {
                        TiledGeometry.intersectX(slice, axTile, ay, bxTile, by, leftLimit);
                    }
                } else if (axTile > rightLimit) {
                    if (bxTile < rightLimit) {
                        TiledGeometry.intersectX(slice, axTile, ay, bxTile, by, rightLimit);
                    }
                } else {
                    slice.addPoint(axTile, ay);
                }
                if (bxTile < leftLimit && axTile >= leftLimit) {
                    TiledGeometry.intersectX(slice, axTile, ay, bxTile, by, leftLimit);
                    exited = true;
                }
                if (bxTile > rightLimit && axTile <= rightLimit) {
                    TiledGeometry.intersectX(slice, axTile, ay, bxTile, by, rightLimit);
                    exited = true;
                }
                if (this.area || !exited) continue;
                xSlices.remove(x2);
            }
        }
        double ax = segment.getX(segment.size() - 1);
        double ay = segment.getY(segment.size() - 1);
        int startX = (int)Math.floor(ax - this.neighborBuffer);
        int endX = (int)Math.floor(ax + this.neighborBuffer);
        for (int x3 = startX - 1; x3 <= endX + 1; ++x3) {
            double axTile = ax - (double)x3;
            MutableCoordinateSequence slice = (MutableCoordinateSequence)((Object)xSlices.get(x3));
            if (slice == null || !(axTile >= leftLimit) || !(axTile <= rightLimit)) continue;
            slice.addPoint(axTile, ay);
        }
        if (this.area) {
            for (IntObjectCursor<MutableCoordinateSequence> cursor : xSlices) {
                ((MutableCoordinateSequence)((Object)cursor.value)).closeRing();
            }
        }
        newGeoms.removeAll((x, value) -> {
            int wrapped = TiledGeometry.wrapX(x, this.maxTilesAtThisZoom);
            return !this.extents.testX(wrapped);
        });
        return newGeoms;
    }

    private IntRangeSet sliceY(CoordinateSequence stripeSegment, int x, boolean outer, Map<TileCoord, List<CoordinateSequence>> inProgressShapes) throws GeometryException {
        double ay;
        double ax;
        if (stripeSegment.size() == 0) {
            return null;
        }
        double leftEdge = -this.buffer;
        double rightEdge = 1.0 + this.buffer;
        TreeSet<Integer> tileYsWithDetail = null;
        IntRangeSet rightFilled = null;
        IntRangeSet leftFilled = null;
        IntObjectHashMap<MutableCoordinateSequence> ySlices = Hppc.newIntObjectHashMap();
        if (x < 0 || x >= this.maxTilesAtThisZoom) {
            return null;
        }
        record SkippedSegment(Direction side, int lo, int hi, boolean asc) {
        }
        ArrayList<SkippedSegment> skipped = null;
        for (int i = 0; i < stripeSegment.size() - 1; ++i) {
            ax = stripeSegment.getX(i);
            ay = stripeSegment.getY(i);
            double bx = stripeSegment.getX(i + 1);
            double by = stripeSegment.getY(i + 1);
            double minY = Math.min(ay, by);
            double maxY = Math.max(ay, by);
            int extentMinY = this.extents.minY();
            int extentMaxY = this.extents.maxY();
            int startY = Math.max(extentMinY, (int)Math.floor(minY - this.neighborBuffer));
            int endStartY = Math.max(extentMinY, (int)Math.floor(minY + this.neighborBuffer));
            int startEndY = Math.min(extentMaxY - 1, (int)Math.floor(maxY - this.neighborBuffer));
            int endY = Math.min(extentMaxY - 1, (int)Math.floor(maxY + this.neighborBuffer));
            boolean onRightEdge = this.area && ax == bx && ax == rightEdge;
            boolean onLeftEdge = this.area && ax == bx && ax == leftEdge;
            for (int y = startY; y <= endY; ++y) {
                if (this.area && y > endStartY && y < startEndY && (onRightEdge || onLeftEdge)) {
                    Integer next;
                    int nextNonEdgeTile;
                    int endSkip;
                    if (tileYsWithDetail == null) {
                        tileYsWithDetail = new TreeSet<Integer>();
                        for (IntCursor cursor : ySlices.keys()) {
                            tileYsWithDetail.add(cursor.value);
                        }
                    }
                    if ((endSkip = (nextNonEdgeTile = (next = tileYsWithDetail.ceiling(y)) == null ? startEndY : Math.min(next, startEndY)) - 1) >= y) {
                        if (skipped == null) {
                            skipped = new ArrayList<SkippedSegment>();
                        }
                        SkippedSegment skippedSegment = new SkippedSegment(onLeftEdge ? Direction.LEFT : Direction.RIGHT, y, endSkip, by > ay);
                        skipped.add(skippedSegment);
                        if (rightFilled == null) {
                            rightFilled = new IntRangeSet();
                            leftFilled = new IntRangeSet();
                        }
                        (onRightEdge ? rightFilled : leftFilled).xor(y, endSkip);
                        y = nextNonEdgeTile;
                    }
                }
                double topLimit = (double)y - this.buffer;
                double bottomLimit = (double)(y + 1) + this.buffer;
                MutableCoordinateSequence slice = ySlices.get(y);
                if (slice == null) {
                    if (tileYsWithDetail != null) {
                        tileYsWithDetail.add(y);
                    }
                    slice = MutableCoordinateSequence.newScalingSequence(0.0, y, 256.0);
                    ySlices.put(y, slice);
                    TileCoord tileID = TileCoord.ofXYZ(x, y, this.z);
                    List toAddTo = inProgressShapes.computeIfAbsent(tileID, tile -> new ArrayList());
                    if (this.area && !outer && toAddTo.isEmpty()) {
                        if (!this.isFilled(x, y)) {
                            throw new GeometryException("bad polygon fill: "+x+", "+y+" is not filled");
                        }
                        toAddTo.add(TiledGeometry.fill(this.buffer));
                    }
                    toAddTo.add(slice);
                    if (this.area && leftFilled != null && skipped != null && (leftFilled.contains(y) || rightFilled.contains(y))) {
                        for (SkippedSegment skippedSegment : skipped) {
                            if (skippedSegment.lo > y || skippedSegment.hi < y) continue;
                            double top = (double)y - this.buffer;
                            double bottom = (double)(y + 1) + this.buffer;
                            double start = skippedSegment.asc ? top : bottom;
                            double end = skippedSegment.asc ? bottom : top;
                            double edgeX = skippedSegment.side == Direction.LEFT ? -this.buffer : 1.0 + this.buffer;
                            slice.addPoint(edgeX, start);
                            slice.addPoint(edgeX, end);
                        }
                    }
                }
                boolean exited = false;
                if (ay < topLimit) {
                    if (by > topLimit) {
                        TiledGeometry.intersectY(slice, ax, ay, bx, by, topLimit);
                    }
                } else if (ay > bottomLimit) {
                    if (by < bottomLimit) {
                        TiledGeometry.intersectY(slice, ax, ay, bx, by, bottomLimit);
                    }
                } else {
                    slice.addPoint(ax, ay);
                }
                if (by < topLimit && ay >= topLimit) {
                    TiledGeometry.intersectY(slice, ax, ay, bx, by, topLimit);
                    exited = true;
                }
                if (by > bottomLimit && ay <= bottomLimit) {
                    TiledGeometry.intersectY(slice, ax, ay, bx, by, bottomLimit);
                    exited = true;
                }
                if (this.area || !exited) continue;
                ySlices.remove(y);
            }
        }
        int last = stripeSegment.size() - 1;
        ax = stripeSegment.getX(last);
        ay = stripeSegment.getY(last);
        int startY = (int)Math.floor(ay - this.neighborBuffer);
        int endY = (int)Math.floor(ay + this.neighborBuffer);
        for (int y = startY - 1; y <= endY + 1; ++y) {
            MutableCoordinateSequence slice = ySlices.get(y);
            double k1 = (double)y - this.buffer;
            double k2 = (double)(y + 1) + this.buffer;
            if (!(ay >= k1) || !(ay <= k2) || slice == null) continue;
            slice.addPoint(ax, ay);
        }
        if (this.area) {
            for (IntObjectCursor<MutableCoordinateSequence> cursor : ySlices) {
                ((MutableCoordinateSequence)((Object)cursor.value)).closeRing();
            }
        }
        return rightFilled != null ? rightFilled.intersect(leftFilled) : null;
    }

    private void addFilledRange(int x, IntRangeSet yRange) {
        IntRangeSet existing;
        if (yRange == null || yRange.isEmpty()) {
            return;
        }
        if (this.filledRanges == null) {
            this.filledRanges = new HashMap<Integer, IntRangeSet>();
        }
        if ((existing = this.filledRanges.get(x)) == null) {
            this.filledRanges.put(x, yRange);
        } else {
            existing.addAll(yRange);
        }
    }

    private void removeFilledRange(int x, IntRangeSet yRange) {
        IntRangeSet existing;
        if (yRange == null || yRange.isEmpty()) {
            return;
        }
        if (this.filledRanges == null) {
            this.filledRanges = new HashMap<Integer, IntRangeSet>();
        }
        if ((existing = this.filledRanges.get(x)) != null) {
            existing.removeAll(yRange);
        }
    }

    private boolean isFilled(int x, int y) {
        if (this.filledRanges == null) {
            return false;
        }
        IntRangeSet filledCol = this.filledRanges.get(x);
        if (filledCol == null) {
            return false;
        }
        return filledCol.contains(y);
    }

    static int encode(long maxTilesAtThisZoom, long x, long y) {
        return (int)(maxTilesAtThisZoom * x + y);
    }

    static TileCoord decode(long maxTilesAtZoom, long tileId, int z) {
        return TileCoord.ofXYZ((int)(tileId / maxTilesAtZoom), (int)(tileId % maxTilesAtZoom), z);
    }

    public static class CoveredTiles
    implements TilePredicate,
    Iterable<TileCoord> {
        private final RoaringBitmap bitmap;
        private final long maxTilesAtZoom;
        private final int z;

        private CoveredTiles(RoaringBitmap bitmap, int z) {
            this.bitmap = bitmap;
            this.maxTilesAtZoom = 1L << z;
            this.z = z;
        }

        public static CoveredTiles merge(CoveredTiles a, CoveredTiles b) {
            if (a.z != b.z) {
                throw new IllegalArgumentException("Cannot combine CoveredTiles with different zoom levels ");
            }
            return new CoveredTiles(RoaringBitmap.or((RoaringBitmap)a.bitmap, (RoaringBitmap)b.bitmap), a.z);
        }

        @Override
        public boolean test(int x, int y) {
            return this.bitmap.contains(TiledGeometry.encode(this.maxTilesAtZoom, x, y));
        }

        public String toString() {
            return "CoveredTiles{z=" + this.z + ", tiles=" + FORMAT.integer(this.bitmap.getCardinality()) + ", storage=" + FORMAT.storage(this.bitmap.getSizeInBytes()) + "B}";
        }

        public Stream<TileCoord> stream() {
            return this.bitmap.stream().mapToObj(i -> TiledGeometry.decode(this.maxTilesAtZoom, Integer.toUnsignedLong(i), this.z));
        }

        @Override
        public Iterator<TileCoord> iterator() {
            final PeekableIntIterator iter = this.bitmap.getIntIterator();
            return new Iterator<TileCoord>(){

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public TileCoord next() {
                    if (!this.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    int i = iter.next();
                    return TiledGeometry.decode(maxTilesAtZoom, Integer.toUnsignedLong(i), z);
                }
            };
        }
    }

    private static enum Direction {
        RIGHT,
        LEFT;

    }
}

