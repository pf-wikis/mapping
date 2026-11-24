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
import java.util.Arrays;
import java.util.List;

import com.carrotsearch.hppc.LongArrayList;

/**
 * Compares uniform fill tiles with their parent tile at the previous zoom.
 *
 * <p>A tile whose features are all fills contains no boundary geometry. Its
 * rendered content only depends on the set of layers/attributes it fills, so it
 * can be replaced by overzooming the parent tile exactly when the parent is also
 * fill-only with the same set. Boundary tiles are always kept: their clipped
 * geometry is what the extra zoom level is for.
 *
 * <p>Signatures are spooled per zoom and read back as runs in tile-id order, so
 * memory use does not grow with the number of tiles.
 */
final class TileSignatureTracker implements AutoCloseable {
	/** Debug switch to produce a baseline archive with every tile stored. */
	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("tile.dropRedundant", "true"));
	/** Never drop more than this many zoom levels in a row. MapLibre underzooms 10. */
	private static final int MAX_CONSECUTIVE_DROPPED_ZOOMS = 8;

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
		Signature signature = signature(features);
		if(signature != null) {
			writer.writeLong(localStart);
			writer.writeLong(count);
			writer.writeLong(signature.a);
			writer.writeLong(signature.b);
		}

		if(signature == null || reader == null || zoom == 0
				|| zoom % (MAX_CONSECUTIVE_DROPPED_ZOOMS + 1) == 0)
			return KEEP_ALL;

		return keptRanges(localStart, count, signature);
	}

	private long[] keptRanges(long localStart, int count, Signature signature) throws IOException {
		long end = localStart + count;
		long parentStart = localStart >>> 2;
		long parentEnd = (end - 1) >>> 2;
		var kept = new LongArrayList();
		long keptStart = -1;

		for(long parent = parentStart; parent <= parentEnd; parent++) {
			long childStart = Math.max(localStart, parent << 2) - localStart;
			boolean drop = reader.hasEntryAt(parent) && reader.matches(signature);
			if(drop) {
				if(keptStart >= 0) {
					kept.add(keptStart);
					kept.add(childStart - keptStart);
					keptStart = -1;
				}
			} else if(keptStart < 0) {
				keptStart = childStart;
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
	 * Order-independent 128-bit signature of the fill feature set. Attribute ids
	 * are stable across zooms, and fill geometry is constant, so equal
	 * signatures mean equal stored fill content.
	 */
	static Signature signature(List<TileStore.TileFeature> features) {
		if(features.isEmpty())
			return null;
		long sum = 0;
		long xor = 0;
		for(var feature : features) {
			if(!feature.fill())
				return null;
			long hash = mix(
				mix(feature.layer() * 0x9E3779B97F4A7C15L + feature.order())
					^ feature.attrs()
			);
			sum += hash;
			xor ^= Long.rotateLeft(hash, 29) + 0x9E3779B97F4A7C15L;
		}
		return new Signature(
			mix(sum + features.size()),
			mix(xor ^ features.size())
		);
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

	private static final class SignatureReader implements Closeable {
		private final DataInputStream in;
		private long start;
		private long end;
		private long a;
		private long b;
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
				return;
			}
			long length = in.readLong();
			a = in.readLong();
			b = in.readLong();
			end = start + length;
			hasEntry = true;
		}

		/** Advances to the entry containing {@code local}, if there is one. */
		boolean hasEntryAt(long local) throws IOException {
			while(hasEntry && end <= local)
				advance();
			return hasEntry && start <= local;
		}

		boolean matches(Signature signature) {
			return a == signature.a && b == signature.b;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
