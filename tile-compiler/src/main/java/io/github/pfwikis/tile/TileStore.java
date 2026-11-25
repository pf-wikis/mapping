package io.github.pfwikis.tile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Stopwatch;

import lombok.extern.slf4j.Slf4j;

/**
 * Bounded-memory tile feature store. Features are accumulated in memory and,
 * once enough of them exist, sorted and spilled to a temporary run file. Run
 * files are later merged by tile id, which keeps peak memory independent of the
 * output tile count (which explodes at high zoom levels).
 */
@Slf4j
final class TileStore implements AutoCloseable {
	private static final long SPILL_THRESHOLD = Long.getLong("tile.spillThreshold", 1_000_000L);

	record TileFeature(int layer, long order, int attrs, TileGeometryCodec.GeometryType type, int[] commands, boolean fill) {}

	private volatile ConcurrentHashMap<Long, List<TileFeature>> tiles = new ConcurrentHashMap<>();
	private final AtomicLong count = new AtomicLong();
	private final ReentrantLock spillLock = new ReentrantLock();
	private final java.util.concurrent.locks.ReadWriteLock mapLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
	private final List<Path> runs = Collections.synchronizedList(new ArrayList<>());
	private final List<Path> fillRuns = Collections.synchronizedList(new ArrayList<>());
	private final AtomicLong fillRunCounter = new AtomicLong();
	private final Path directory;

	TileStore(Path directory) throws IOException {
		this.directory = Files.createDirectories(directory);
	}

	/** Writes sorted, contiguous {@code (start, length)} tile-id run pairs. */
	void addFillChunk(TileFeature feature, long[] runs) {
		if(runs.length == 0)
			return;
		try {
			var path = directory.resolve("fill-" + fillRunCounter.getAndIncrement() + ".bin");
			try(var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path), 1 << 16))) {
				writeVarInt(out, feature.layer());
				writeVarLong(out, feature.order());
				writeVarInt(out, feature.attrs());
				out.writeByte(feature.type().ordinal());
				writeVarInt(out, feature.commands().length);
				for(int command : feature.commands())
					writeVarInt(out, zigZagEncode(command));
				writeVarInt(out, runs.length / 2);
				long previousEnd = 0;
				for(int i=0; i<runs.length; i+=2) {
					long start = runs[i];
					long length = runs[i+1];
					writeVarLong(out, start - previousEnd);
					writeVarLong(out, length);
					previousEnd = start + length;
				}
			}
			fillRuns.add(path);
		} catch(IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	List<Path> fillRuns() {
		return fillRuns;
	}

	void add(long tileId, TileFeature feature) {
		mapLock.readLock().lock();
		try {
			tiles.computeIfAbsent(tileId, id -> Collections.synchronizedList(new ArrayList<>())).add(feature);
		} finally {
			mapLock.readLock().unlock();
		}
		if(count.incrementAndGet() >= SPILL_THRESHOLD)
			maybeSpill();
	}

	private void maybeSpill() {
		if(!spillLock.tryLock()) {
			// Do not let the live map grow without bound while another thread spills.
			if(count.get() >= SPILL_THRESHOLD * 2) {
				spillLock.lock();
				try {
					if(count.get() >= SPILL_THRESHOLD)
						spillCurrent();
				} finally {
					spillLock.unlock();
				}
			}
			return;
		}
		try {
			if(count.get() >= SPILL_THRESHOLD)
				spillCurrent();
		} finally {
			spillLock.unlock();
		}
	}

	private void spillCurrent() {
		var snapshot = tiles;
		mapLock.writeLock().lock();
		try {
			snapshot = tiles;
			tiles = new ConcurrentHashMap<>();
			count.set(0);
		} finally {
			mapLock.writeLock().unlock();
		}
		if(snapshot.isEmpty())
			return;
		try {
			spill(snapshot);
		} catch(IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private void spill(Map<Long, List<TileFeature>> snapshot) throws IOException {
		var entries = new ArrayList<RunEntry>();
		for(var tile : snapshot.entrySet()) {
			synchronized(tile.getValue()) {
				for(var feature : tile.getValue())
					entries.add(new RunEntry(tile.getKey(), feature));
			}
		}
		entries.sort(Comparator.comparingLong(RunEntry::tileId)
			.thenComparingInt(e -> e.feature().layer())
			.thenComparingLong(e -> e.feature().order()));

		var path = directory.resolve("run-" + runs.size() + ".bin");
		var watch = Stopwatch.createStarted();
		try(var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path), 1 << 20))) {
			int i = 0;
			while(i < entries.size()) {
				long tileId = entries.get(i).tileId();
				int end = i + 1;
				while(end < entries.size() && entries.get(end).tileId() == tileId)
					end++;
				out.writeLong(tileId);
				writeVarInt(out, end - i);
				for(int j = i; j < end; j++) {
					var feature = entries.get(j).feature();
					writeVarInt(out, feature.layer());
					writeVarInt(out, feature.attrs());
					writeVarLong(out, feature.order());
					out.writeByte(feature.type().ordinal());
					out.writeBoolean(feature.fill());
					writeVarInt(out, feature.commands().length);
					for(int command : feature.commands())
						writeVarInt(out, zigZagEncode(command));
				}
				i = end;
			}
		}
		runs.add(path);
		log.info("Spilled run {} ({} tile features) in {}ms", runs.size()-1, entries.size(), watch.elapsed(TimeUnit.MILLISECONDS));
	}

	/** Spills any remaining in-memory features so all data is available as sorted runs. */
	List<Path> finish() {
		spillLock.lock();
		try {
			spillCurrent();
		} finally {
			spillLock.unlock();
		}
		return List.copyOf(runs);
	}

	@Override
	public void close() {
		for(var run : runs) {
			try {
				Files.deleteIfExists(run);
			} catch(IOException ignored) {
			}
		}
		for(var run : fillRuns) {
			try {
				Files.deleteIfExists(run);
			} catch(IOException ignored) {
			}
		}
		try {
			Files.deleteIfExists(directory);
		} catch(IOException ignored) {
		}
	}

	private record RunEntry(long tileId, TileFeature feature) {}

	static final class RunReader implements Closeable {
		private final DataInputStream in;
		private long tileId;
		private List<TileFeature> features;

		RunReader(Path path) throws IOException {
			in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path), 1 << 20));
		}

		long tileId() {
			return tileId;
		}

		List<TileFeature> features() {
			return features;
		}

		boolean advance() throws IOException {
			try {
				tileId = in.readLong();
			} catch(EOFException e) {
				return false;
			}
			int size = readVarInt(in);
			var list = new ArrayList<TileFeature>(size);
			for(int i=0; i<size; i++) {
				int layer = readVarInt(in);
				int attrs = readVarInt(in);
				long order = readVarLong(in);
				var type = TileGeometryCodec.GeometryType.values()[in.readUnsignedByte()];
				boolean fill = in.readBoolean();
				int length = readVarInt(in);
				var commands = new int[length];
				for(int j=0; j<length; j++)
					commands[j] = zigZagDecode(readVarInt(in));
				list.add(new TileFeature(layer, order, attrs, type, commands, fill));
			}
			features = list;
			return true;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}

	static void writeVarInt(OutputStream out, int value) throws IOException {
		while((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	static void writeVarLong(OutputStream out, long value) throws IOException {
		while((value & ~0x7FL) != 0) {
			out.write((int)(value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write((int)value);
	}

	static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		int shift = 0;
		while(true) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if((b & 0x80) == 0)
				return value;
			shift += 7;
		}
	}

	static long readVarLong(DataInputStream in) throws IOException {
		long value = 0;
		int shift = 0;
		while(true) {
			int b = in.readUnsignedByte();
			value |= (long)(b & 0x7F) << shift;
			if((b & 0x80) == 0)
				return value;
			shift += 7;
		}
	}

	static int zigZagEncode(int n) {
		return n << 1 ^ n >> 31;
	}

	static int zigZagDecode(int n) {
		return n >> 1 ^ -(n & 1);
	}
}
