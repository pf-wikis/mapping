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

import com.carrotsearch.hppc.LongArrayList;

/**
 * Compares low-detail tiles with their parent tile at the previous zoom.
 *
 * <p>A low-detail tile may contain only uniform fill features and point
 * features. Boundary geometry (lines and non-fill polygons) is never dropped:
 * that is where the extra zoom level actually adds detail.
 *
 * <p>A low-detail child is dropped when:
 * <ul>
 *   <li>its fill features and their attributes are exactly the same as the
 *       parent's;</li>
 *   <li>it has the same point features and attributes as the part of the parent
 *       that overlaps the child;</li>
 *   <li>those points are within 0.1% of a tile extent of the parent's
 *       overzoomed location.</li>
 * </ul>
 *
 * <p>Signatures and point data are spooled per zoom and read back in tile-id
 * order, so memory use does not grow with the number of tiles.
 */
final class TileSignatureTracker implements AutoCloseable {
	/** Debug switch to produce a baseline archive with every tile stored. */
	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("tile.dropRedundant", "true"));
	/** Never drop more than this many zoom levels in a row. MapLibre underzooms 10. */
	private static final int MAX_CONSECUTIVE_DROPPED_ZOOMS = 8;

	private static final double TILE_EXTENT = 4096d;
	private static final double POINT_ERROR = TILE_EXTENT * 0.001;
	private static final double POINT_BUFFER = TILE_EXTENT * 4d / 256d + 1d;

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
		if(analysis == null || count > 1 && !analysis.points.isEmpty())
			return KEEP_ALL;

		long[] kept;
		boolean forced = zoom == 0 || zoom % (MAX_CONSECUTIVE_DROPPED_ZOOMS + 1) == 0;
		if(forced || reader == null)
			kept = KEEP_ALL;
		else
			kept = computeKept(localStart, count, analysis);

		List<PointFeature> reference;
		if(count > 1) {
			reference = List.of();
		} else if(kept == KEEP_ALL) {
			// This tile is stored, so it becomes the new reference itself.
			reference = analysis.points;
		} else {
			// This tile was dropped. Keep referring to the first stored ancestor's
			// positions, transformed into this tile's frame, so repeated drops do
			// not accumulate positional error.
			TileCoord coord = TileCoord.hilbertDecode(TileCoord.startIndexForZoom(zoom) + localStart);
			reference = transform(reader.points(), coord.x() & 1, coord.y() & 1);
		}
		write(localStart, count, analysis.fill, reference);
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

			if(reader.points().isEmpty()) {
				if(keptStart >= 0) {
					kept.add(keptStart);
					kept.add(segmentStart - localStart - keptStart);
					keptStart = -1;
				}
				continue;
			}

			// The parent has points, so the decision can differ for each of its
			// four children. Check them individually.
			for(long child = segmentStart; child < segmentEnd; child++) {
				long offset = child - localStart;
				TileCoord coord = TileCoord.hilbertDecode(TileCoord.startIndexForZoom(trackedZoom) + child);
				boolean drop = pointsMatch(analysis, reader.points(), coord.x() & 1, coord.y() & 1);
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

	private void write(long localStart, int count, Signature fill, List<PointFeature> points) throws IOException {
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
	}

	/** Maps the stored ancestor's points into one child tile's coordinate frame. */
	private static List<PointFeature> transform(List<PointFeature> parent, int dx, int dy) {
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
				if(x < -POINT_BUFFER || x > TILE_EXTENT + POINT_BUFFER
						|| y < -POINT_BUFFER || y > TILE_EXTENT + POINT_BUFFER)
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
	 * Splits the features into fill content and point content. Returns
	 * {@code null} for tiles containing boundary geometry, which are never
	 * candidates for dropping.
	 */
	private static Analysis analyze(List<TileStore.TileFeature> features) {
		if(features.isEmpty())
			return null;

		long sum = 0;
		long xor = 0;
		int fills = 0;
		var points = new ArrayList<PointFeature>();

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
			if(feature.type() != TileGeometryCodec.GeometryType.POINT)
				return null;

			try {
				var geometry = TileGeometryCodec.decode(
					new TileGeometryCodec.EncodedGeometry(feature.type(), feature.commands())
				);
				var coordinates = geometry.getCoordinates();
				if(coordinates.length == 0)
					continue;
				int[] xs = new int[coordinates.length];
				int[] ys = new int[coordinates.length];
				for(int i=0; i<coordinates.length; i++) {
					xs[i] = (int)Math.round(coordinates[i].x);
					ys[i] = (int)Math.round(coordinates[i].y);
				}
				points.add(new PointFeature(feature.layer(), feature.order(), feature.attrs(), xs, ys));
			} catch(GeometryException e) {
				return null;
			}
		}

		var fill = new Signature(
			mix(sum + fills),
			mix(xor ^ fills)
		);
		return new Analysis(fill, points);
	}

	private static boolean pointsMatch(Analysis child, List<PointFeature> parentPoints, int dx, int dy) {
		var expected = new HashMap<PointKey, List<double[]>>();
		for(var feature : parentPoints) {
			var key = new PointKey(feature.layer, feature.order, feature.attrs);
			List<double[]> list = null;
			for(int i=0; i<feature.xs.length; i++) {
				double x = 2d * feature.xs[i] - TILE_EXTENT * dx;
				double y = 2d * feature.ys[i] - TILE_EXTENT * dy;
				if(x < -POINT_BUFFER || x > TILE_EXTENT + POINT_BUFFER
						|| y < -POINT_BUFFER || y > TILE_EXTENT + POINT_BUFFER)
					continue;
				if(list == null)
					list = expected.computeIfAbsent(key, k -> new ArrayList<>());
				list.add(new double[] {x, y});
			}
		}

		if(child.points.isEmpty())
			return expected.isEmpty();

		var actual = new HashMap<PointKey, List<double[]>>();
		for(var feature : child.points) {
			var key = new PointKey(feature.layer, feature.order, feature.attrs);
			var list = actual.computeIfAbsent(key, k -> new ArrayList<>());
			for(int i=0; i<feature.xs.length; i++)
				list.add(new double[] {feature.xs[i], feature.ys[i]});
		}

		if(actual.size() != expected.size())
			return false;
		double tolerance = POINT_ERROR * POINT_ERROR;
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
	private record Analysis(Signature fill, List<PointFeature> points) {}
	private record PointFeature(int layer, long order, int attrs, int[] xs, int[] ys) {}
	private record PointKey(int layer, long order, int attrs) {}

	private static final class SignatureReader implements Closeable {
		private final DataInputStream in;
		private long start;
		private long end;
		private long fillA;
		private long fillB;
		private List<PointFeature> points;
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
				return;
			}
			long length = in.readLong();
			fillA = in.readLong();
			fillB = in.readLong();
			int count = in.readInt();
			if(count == 0) {
				points = List.of();
			} else {
				var list = new ArrayList<PointFeature>(count);
				for(int i=0; i<count; i++) {
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

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
