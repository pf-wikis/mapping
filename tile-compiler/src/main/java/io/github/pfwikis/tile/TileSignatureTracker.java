package io.github.pfwikis.tile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import com.carrotsearch.hppc.LongArrayList;

/**
 * Compares each tile with its first stored ancestor and drops it when the
 * ancestor already carries the same information.
 *
 * <p>Compared content:
 * <ul>
 *   <li>uniform fill features by an exact set/attribute signature;</li>
 *   <li>point features by layer/order/attributes and position within 0.1% of a
 *       tile extent;</li>
 *   <li>boundary geometry (lines and non-fill polygons) by clipping the
 *       ancestor geometry into the child tile and comparing shapes within the
 *       same 0.1% extent tolerance.</li>
 * </ul>
 *
 * <p>When a tile is dropped, its entry stores references to the first stored
 * ancestor (transformed into that tile's frame), so repeated drops are measured
 * against the original stored content instead of accumulating error.
 *
 * <p>Entries are spooled per zoom and read back in tile-id order, keeping
 * memory use bounded.
 */
final class TileSignatureTracker implements AutoCloseable {
	/** Debug switch to produce a baseline archive with every tile stored. */
	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("tile.dropRedundant", "true"));
	/** Never drop more than this many zoom levels in a row. MapLibre underzooms 10. */
	private static final int MAX_CONSECUTIVE_DROPPED_ZOOMS = 8;

	private static final double TILE_EXTENT = 4096d;
	private static final double POSITION_ERROR = TILE_EXTENT * 0.001;
	private static final double BUFFER = TILE_EXTENT * 4d / 256d + 1d;
	private static final Envelope BUFFERED_EXTENT =
		new Envelope(-BUFFER, TILE_EXTENT + BUFFER, -BUFFER, TILE_EXTENT + BUFFER);
	private static final Geometry CLIP = GeoUtils.JTS_FACTORY.toGeometry(BUFFERED_EXTENT);
	private static final WKBWriter WKB_WRITER = new WKBWriter();
	private static final WKBReader WKB_READER = new WKBReader();

	private static final long[] KEEP_ALL = null;
	private static final long[] DROP_ALL = new long[0];

	private final Path directory;
	private int trackedZoom = -1;
	private Path trackedFile;
	private DataOutputStream writer;
	private SignatureReader reader;
	private Path readerFile;

	TileSignatureTracker(Path directory) throws IOException {
		this.directory = Files.createDirectories(directory);
	}

	/**
	 * Records the run for the next zoom and returns the local
	 * {@code (start, length)} ranges that have to be written.
	 *
	 * @return {@code null} to keep the whole run, an empty array to drop it, or
	 *         pairs of local start offsets and lengths
	 */
	long[] keptRanges(long tileId, int count, List<TileStore.TileFeature> features) throws IOException {
		if(!ENABLED)
			return KEEP_ALL;

		int zoom = TileCoord.zoomForIndex(tileId);
		transition(zoom);

		long localStart = tileId - TileCoord.startIndexForZoom(zoom);
		Analysis analysis = analyze(features);
		// Multi-tile runs can only contain fills. Anything else is left to the
		// normal writer and is not used as an ancestor reference.
		if(analysis == null || count > 1 && (!analysis.points.isEmpty() || !analysis.boundaries.isEmpty()))
			return KEEP_ALL;

		long[] kept;
		boolean forced = zoom == 0 || zoom % (MAX_CONSECUTIVE_DROPPED_ZOOMS + 1) == 0;
		if(forced || reader == null)
			kept = KEEP_ALL;
		else
			kept = computeKept(localStart, count, analysis);

		if(count > 1) {
			write(localStart, count, analysis.fill, List.of(), List.of());
			return kept;
		}

		TileCoord coord = TileCoord.hilbertDecode(TileCoord.startIndexForZoom(zoom) + localStart);
		int dx = coord.x() & 1;
		int dy = coord.y() & 1;

		List<PointFeature> points;
		List<BoundaryFeature> boundaries;
		if(kept == KEEP_ALL) {
			// This tile is stored and becomes the new reference.
			points = analysis.points;
			boundaries = analysis.boundaries;
		} else {
			// Keep referring to the first stored ancestor, transformed into this
			// tile's frame. This prevents error accumulation across drop chains.
			points = transformPoints(reader.points(), dx, dy);
			boundaries = transformBoundaries(reader.boundaries(), dx, dy);
		}
		write(localStart, count, analysis.fill, points, boundaries);
		return kept;
	}

	private long[] computeKept(long localStart, int count, Analysis analysis) throws IOException {
		long end = localStart + count;
		long parentStart = localStart >>> 2;
		long parentEnd = (end - 1) >>> 2;
		var kept = new LongArrayList();
		long keptStart = -1;

		for(long parent = parentStart; parent <= parentEnd; parent++) {
			long segmentStart = Math.max(localStart, parent << 2);
			long segmentEnd = Math.min(end, (parent << 2) + 4);

			if(!reader.hasEntryAt(parent) || !reader.matchesFill(analysis.fill)) {
				if(keptStart < 0)
					keptStart = segmentStart - localStart;
				continue;
			}

			if(reader.points().isEmpty() && reader.boundaries().isEmpty()) {
				if(keptStart >= 0) {
					kept.add(keptStart);
					kept.add(segmentStart - localStart - keptStart);
					keptStart = -1;
				}
				continue;
			}

			for(long child = segmentStart; child < segmentEnd; child++) {
				long offset = child - localStart;
				TileCoord coord = TileCoord.hilbertDecode(TileCoord.startIndexForZoom(trackedZoom) + child);
				int dx = coord.x() & 1;
				int dy = coord.y() & 1;
				boolean drop = pointsMatch(analysis.points, reader.points(), dx, dy)
					&& boundariesMatch(analysis.boundaries, reader.boundaries(), dx, dy);
				if(drop) {
					if(keptStart >= 0) {
						kept.add(keptStart);
						kept.add(offset - keptStart);
						keptStart = -1;
					}
				} else if(keptStart < 0) {
					keptStart = offset;
				}
			}
		}

		if(keptStart >= 0) {
			kept.add(keptStart);
			kept.add(count - keptStart);
		}

		if(kept.isEmpty())
			return DROP_ALL;
		if(kept.size() == 2 && kept.get(0) == 0 && kept.get(1) == count)
			return KEEP_ALL;
		return Arrays.copyOf(kept.buffer, kept.size());
	}

	private static boolean boundariesMatch(List<BoundaryFeature> child, List<BoundaryFeature> parent, int dx, int dy) {
		if(child.isEmpty() && parent.isEmpty())
			return true;

		var expected = new HashMap<FeatureKey, Geometry>();
		for(var feature : parent) {
			Geometry clipped = transformClip(feature.geometry, dx, dy);
			if(clipped != null && !clipped.isEmpty())
				expected.put(new FeatureKey(feature.layer, feature.order, feature.attrs), clipped);
		}
		if(child.isEmpty())
			return expected.isEmpty();

		var actual = new HashMap<FeatureKey, Geometry>();
		for(var feature : child)
			actual.put(new FeatureKey(feature.layer, feature.order, feature.attrs), feature.geometry);

		if(actual.size() != expected.size())
			return false;

		for(var entry : actual.entrySet()) {
			Geometry want = expected.get(entry.getKey());
			if(want == null || !geometryMatches(entry.getValue(), want))
				return false;
		}
		return true;
	}

	private static boolean geometryMatches(Geometry child, Geometry parent) {
		Geometry a = boundaryOf(child);
		Geometry b = boundaryOf(parent);
		if(a.isEmpty() || b.isEmpty())
			return a.isEmpty() && b.isEmpty();
		try {
			// Densify so long simplified segments are sampled, not just vertices.
			return DiscreteHausdorffDistance.distance(a, b, 0.25) <= POSITION_ERROR;
		} catch(RuntimeException e) {
			return false;
		}
	}

	private static Geometry boundaryOf(Geometry geometry) {
		return geometry instanceof Polygon || geometry instanceof MultiPolygon
			? geometry.getBoundary()
			: geometry;
	}

	private static Geometry transformClip(Geometry geometry, int dx, int dy) {
		var transform = new AffineTransformation(
			2, 0, -TILE_EXTENT * dx,
			0, 2, -TILE_EXTENT * dy
		);
		try {
			return transform.transform(geometry).intersection(CLIP);
		} catch(RuntimeException e) {
			return null;
		}
	}

	private static List<PointFeature> transformPoints(List<PointFeature> parent, int dx, int dy) {
		if(parent.isEmpty())
			return List.of();
		var result = new ArrayList<PointFeature>(parent.size());
		for(var feature : parent) {
			int[] xs = new int[feature.xs.length];
			int[] ys = new int[feature.xs.length];
			int count = 0;
			for(int i=0; i<feature.xs.length; i++) {
				double x = 2d * feature.xs[i] - TILE_EXTENT * dx;
				double y = 2d * feature.ys[i] - TILE_EXTENT * dy;
				if(x < -BUFFER || x > TILE_EXTENT + BUFFER || y < -BUFFER || y > TILE_EXTENT + BUFFER)
					continue;
				xs[count] = (int)Math.round(x);
				ys[count] = (int)Math.round(y);
				count++;
			}
			if(count == 0)
				continue;
			result.add(new PointFeature(
				feature.layer,
				feature.order,
				feature.attrs,
				count == xs.length ? xs : Arrays.copyOf(xs, count),
				count == ys.length ? ys : Arrays.copyOf(ys, count)
			));
		}
		return result;
	}

	private static List<BoundaryFeature> transformBoundaries(List<BoundaryFeature> parent, int dx, int dy) {
		if(parent.isEmpty())
			return List.of();
		var result = new ArrayList<BoundaryFeature>(parent.size());
		for(var feature : parent) {
			Geometry clipped = transformClip(feature.geometry, dx, dy);
			if(clipped != null && !clipped.isEmpty())
				result.add(new BoundaryFeature(feature.layer, feature.order, feature.attrs, clipped));
		}
		return result;
	}

	private static boolean pointsMatch(List<PointFeature> child, List<PointFeature> parentPoints, int dx, int dy) {
		var expected = new HashMap<FeatureKey, List<double[]>>();
		for(var feature : parentPoints) {
			var key = new FeatureKey(feature.layer, feature.order, feature.attrs);
			List<double[]> list = null;
			for(int i=0; i<feature.xs.length; i++) {
				double x = 2d * feature.xs[i] - TILE_EXTENT * dx;
				double y = 2d * feature.ys[i] - TILE_EXTENT * dy;
				if(x < -BUFFER || x > TILE_EXTENT + BUFFER || y < -BUFFER || y > TILE_EXTENT + BUFFER)
					continue;
				if(list == null)
					list = expected.computeIfAbsent(key, k -> new ArrayList<>());
				list.add(new double[] {x, y});
			}
		}

		if(child.isEmpty())
			return expected.isEmpty();

		var actual = new HashMap<FeatureKey, List<double[]>>();
		for(var feature : child) {
			var key = new FeatureKey(feature.layer, feature.order, feature.attrs);
			var list = actual.computeIfAbsent(key, k -> new ArrayList<>());
			for(int i=0; i<feature.xs.length; i++)
				list.add(new double[] {feature.xs[i], feature.ys[i]});
		}

		if(actual.size() != expected.size())
			return false;
		double tolerance = POSITION_ERROR * POSITION_ERROR;
		for(var entry : actual.entrySet()) {
			var want = expected.get(entry.getKey());
			if(want == null || want.size() != entry.getValue().size())
				return false;
			boolean[] used = new boolean[want.size()];
			for(var point : entry.getValue()) {
				boolean matched = false;
				for(int i=0; i<want.size(); i++) {
					if(used[i])
						continue;
					double ddx = point[0] - want.get(i)[0];
					double ddy = point[1] - want.get(i)[1];
					if(ddx*ddx + ddy*ddy <= tolerance) {
						used[i] = true;
						matched = true;
						break;
					}
				}
				if(!matched)
					return false;
			}
		}
		return true;
	}

	private void write(long localStart, int count, Signature fill, List<PointFeature> points,
		List<BoundaryFeature> boundaries) throws IOException {
		writer.writeLong(localStart);
		writer.writeLong(count);
		writer.writeLong(fill.a);
		writer.writeLong(fill.b);
		writer.writeInt(points.size());
		for(var point : points) {
			writer.writeInt(point.layer);
			writer.writeLong(point.order);
			writer.writeInt(point.attrs);
			writer.writeInt(point.xs.length);
			for(int i=0; i<point.xs.length; i++) {
				writer.writeInt(point.xs[i]);
				writer.writeInt(point.ys[i]);
			}
		}
		writer.writeInt(boundaries.size());
		for(var feature : boundaries) {
			writer.writeInt(feature.layer);
			writer.writeLong(feature.order);
			writer.writeInt(feature.attrs);
			byte[] wkb = WKB_WRITER.write(feature.geometry);
			writer.writeInt(wkb.length);
			writer.write(wkb);
		}
	}

	private void transition(int zoom) throws IOException {
		if(zoom == trackedZoom)
			return;

		Path previous = trackedFile;
		int previousZoom = trackedZoom;

		if(reader != null) {
			reader.close();
			reader = null;
			if(readerFile != null) {
				Files.deleteIfExists(readerFile);
				readerFile = null;
			}
		}
		if(writer != null) {
			writer.close();
			writer = null;
		}

		trackedZoom = zoom;
		trackedFile = directory.resolve("signatures-"+zoom+".bin");
		writer = new DataOutputStream(new BufferedOutputStream(
			Files.newOutputStream(trackedFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
			1 << 20
		));

		if(previousZoom == zoom - 1 && previous != null) {
			reader = new SignatureReader(previous);
			readerFile = previous;
		}
	}

	/**
	 * Splits a tile into fill content, point content and boundary content.
	 * Returns {@code null} only for empty tiles or undecodable geometry, which
	 * are never used as ancestors.
	 */
	private static Analysis analyze(List<TileStore.TileFeature> features) {
		if(features.isEmpty())
			return null;

		long sum = 0;
		long xor = 0;
		int fills = 0;
		var points = new ArrayList<PointFeature>();
		var boundaries = new ArrayList<BoundaryFeature>();

		for(var feature : features) {
			if(feature.fill()) {
				long hash = mix(
					mix(feature.layer() * 0x9E3779B97F4A7C15L + feature.order())
						^ feature.attrs()
				);
				sum += hash;
				xor ^= Long.rotateLeft(hash, 29) + 0x9E3779B97F4A7C15L;
				fills++;
				continue;
			}

			Geometry geometry;
			try {
				geometry = TileGeometryCodec.decode(
					new TileGeometryCodec.EncodedGeometry(feature.type(), feature.commands())
				);
			} catch(GeometryException e) {
				return null;
			}
			if(geometry.isEmpty())
				continue;

			if(feature.type() == TileGeometryCodec.GeometryType.POINT) {
				var coordinates = geometry.getCoordinates();
				int[] xs = new int[coordinates.length];
				int[] ys = new int[coordinates.length];
				for(int i=0; i<coordinates.length; i++) {
					xs[i] = (int)Math.round(coordinates[i].x);
					ys[i] = (int)Math.round(coordinates[i].y);
				}
				points.add(new PointFeature(feature.layer(), feature.order(), feature.attrs(), xs, ys));
			} else {
				boundaries.add(new BoundaryFeature(
					feature.layer(), feature.order(), feature.attrs(), geometry
				));
			}
		}

		var fill = new Signature(
			mix(sum + fills),
			mix(xor ^ fills)
		);
		return new Analysis(fill, points, boundaries);
	}

	private static long mix(long value) {
		value += 0x9E3779B97F4A7C15L;
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	@Override
	public void close() throws IOException {
		if(reader != null) {
			reader.close();
			reader = null;
		}
		if(writer != null) {
			writer.close();
			writer = null;
		}
		if(readerFile != null)
			Files.deleteIfExists(readerFile);
		if(trackedFile != null)
			Files.deleteIfExists(trackedFile);
	}

	record Signature(long a, long b) {}
	private record Analysis(Signature fill, List<PointFeature> points, List<BoundaryFeature> boundaries) {}
	private record PointFeature(int layer, long order, int attrs, int[] xs, int[] ys) {}
	private record BoundaryFeature(int layer, long order, int attrs, Geometry geometry) {}
	private record FeatureKey(int layer, long order, int attrs) {}

	private static final class SignatureReader implements Closeable {
		private final DataInputStream in;
		private long start;
		private long end;
		private long fillA;
		private long fillB;
		private List<PointFeature> points;
		private List<BoundaryFeature> boundaries;
		private boolean hasEntry;

		SignatureReader(Path path) throws IOException {
			in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path), 1 << 20));
			advance();
		}

		private void advance() throws IOException {
			try {
				start = in.readLong();
			} catch(EOFException e) {
				hasEntry = false;
				points = List.of();
				boundaries = List.of();
				return;
			}
			long length = in.readLong();
			fillA = in.readLong();
			fillB = in.readLong();

			int pointCount = in.readInt();
			if(pointCount == 0) {
				points = List.of();
			} else {
				var list = new ArrayList<PointFeature>(pointCount);
				for(int i=0; i<pointCount; i++) {
					int layer = in.readInt();
					long order = in.readLong();
					int attrs = in.readInt();
					int numPoints = in.readInt();
					int[] xs = new int[numPoints];
					int[] ys = new int[numPoints];
					for(int j=0; j<numPoints; j++) {
						xs[j] = in.readInt();
						ys[j] = in.readInt();
					}
					list.add(new PointFeature(layer, order, attrs, xs, ys));
				}
				points = list;
			}

			int boundaryCount = in.readInt();
			if(boundaryCount == 0) {
				boundaries = List.of();
			} else {
				var list = new ArrayList<BoundaryFeature>(boundaryCount);
				for(int i=0; i<boundaryCount; i++) {
					int layer = in.readInt();
					long order = in.readLong();
					int attrs = in.readInt();
					int bytes = in.readInt();
					byte[] wkb = new byte[bytes];
					in.readFully(wkb);
					try {
						list.add(new BoundaryFeature(layer, order, attrs, WKB_READER.read(wkb)));
					} catch(ParseException e) {
						throw new IOException("Could not read boundary geometry", e);
					}
				}
				boundaries = list;
			}
			end = start + length;
			hasEntry = true;
		}

		/** Advances to the entry containing {@code local}, if there is one. */
		boolean hasEntryAt(long local) throws IOException {
			while(hasEntry && end <= local)
				advance();
			return hasEntry && start <= local;
		}

		boolean matchesFill(Signature signature) {
			return fillA == signature.a && fillB == signature.b;
		}

		List<PointFeature> points() {
			return points;
		}

		List<BoundaryFeature> boundaries() {
			return boundaries;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
