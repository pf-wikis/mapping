package io.github.pfwikis.tile;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Sweeps the sorted boundary runs and the (start, length) fill runs produced by
 * {@link TileStore}. The result is a stream of runs whose tile content is
 * constant, which lets the compiler encode each fill combination once and lets
 * the PMTiles writer run-length compress hundreds of millions of addressed
 * tiles into a few million entries.
 */
final class TileMerger implements Closeable {
	private final List<TileStore.RunReader> readers = new ArrayList<>();
	private final List<FillReader> fillReaders = new ArrayList<>();
	private final PriorityQueue<TileStore.RunReader> boundaries =
		new PriorityQueue<>(Comparator.comparingLong(TileStore.RunReader::tileId));
	private final PriorityQueue<FillRun> starts =
		new PriorityQueue<>(Comparator.comparingLong(FillRun::start));
	private final PriorityQueue<FillRun> ends =
		new PriorityQueue<>(Comparator.comparingLong(FillRun::end));
	private final Map<FeatureKey, ActiveFill> active = new HashMap<>();
	private long position = -1;
	private long runTileId;
	private int runCount;
	private List<TileStore.TileFeature> features;

	TileMerger(List<Path> runs, List<Path> fillRuns) throws IOException {
		for(var run : runs) {
			var reader = new TileStore.RunReader(run);
			readers.add(reader);
			if(reader.advance())
				boundaries.add(reader);
		}
		for(var run : fillRuns) {
			var reader = new FillReader(run);
			fillReaders.add(reader);
			if(reader.loadNextRun())
				starts.add(reader.run());
		}
	}

	boolean nextRun() throws IOException {
		while(true) {
			if(position < 0) {
				long next = nextEvent();
				if(next == Long.MAX_VALUE)
					return false;
				position = next;
			}
			removeEnding(position);
			addStarting(position);

			var boundaryFeatures = takeBoundary(position);
			if(!boundaryFeatures.isEmpty()) {
				var combined = new ArrayList<TileStore.TileFeature>(activeFeatureCount() + boundaryFeatures.size());
				addActiveFeatures(combined);
				combined.addAll(boundaryFeatures);
				sort(combined);
				runTileId = position++;
				runCount = 1;
				features = combined;
				return true;
			}

			long next = nextEvent();
			if(active.isEmpty()) {
				if(next == Long.MAX_VALUE)
					return false;
				position = next;
				continue;
			}

			if(next == Long.MAX_VALUE || next <= position)
				throw new IllegalStateException("fill sweep stuck at "+position+" next="+next+" active="+active.size()
					+" starts="+starts.size()+" ends="+ends.size()+" boundaries="+boundaries.size());
			var runFeatures = new ArrayList<TileStore.TileFeature>(activeFeatureCount());
			addActiveFeatures(runFeatures);
			sort(runFeatures);
			runTileId = position;
			runCount = (int)Math.min(Integer.MAX_VALUE, next - position);
			position = next;
			features = runFeatures;
			return true;
		}
	}

	private long nextEvent() {
		long next = Long.MAX_VALUE;
		if(!boundaries.isEmpty())
			next = Math.min(next, boundaries.peek().tileId());
		if(!starts.isEmpty())
			next = Math.min(next, starts.peek().start());
		if(!ends.isEmpty())
			next = Math.min(next, ends.peek().end());
		return next;
	}

	private void removeEnding(long tileId) {
		while(!ends.isEmpty() && ends.peek().end() == tileId) {
			var run = ends.poll();
			var current = active.get(run.key());
			if(current != null && --current.count == 0)
				active.remove(run.key());
		}
	}

	private void addStarting(long tileId) throws IOException {
		while(!starts.isEmpty() && starts.peek().start() == tileId) {
			var run = starts.poll();
			ends.add(run);
			var current = active.get(run.key());
			if(current == null)
				active.put(run.key(), new ActiveFill(run.feature(), 1));
			else
				current.count++;
			if(run.reader().loadNextRun())
				starts.add(run.reader().run());
		}
	}

	private List<TileStore.TileFeature> takeBoundary(long tileId) throws IOException {
		var result = new ArrayList<TileStore.TileFeature>(0);
		while(!boundaries.isEmpty() && boundaries.peek().tileId() == tileId) {
			var reader = boundaries.poll();
			result.addAll(reader.features());
			if(reader.advance())
				boundaries.add(reader);
		}
		return result;
	}

	private int activeFeatureCount() {
		int count = 0;
		for(var fill : active.values())
			count += fill.count;
		return count;
	}

	private void addActiveFeatures(List<TileStore.TileFeature> target) {
		for(var fill : active.values()) {
			for(int i=0; i<fill.count; i++)
				target.add(fill.feature);
		}
	}

	private static void sort(List<TileStore.TileFeature> features) {
		if(features.size() > 1) {
			features.sort(Comparator
				.comparingInt(TileStore.TileFeature::layer)
				.thenComparingLong(TileStore.TileFeature::order));
		}
	}

	long runTileId() {
		return runTileId;
	}

	int runCount() {
		return runCount;
	}

	List<TileStore.TileFeature> features() {
		return features;
	}

	@Override
	public void close() throws IOException {
		for(var reader : readers)
			reader.close();
		for(var reader : fillReaders)
			reader.close();
	}

	private record FeatureKey(int layer, long order, int attrs) {}

	private record FillRun(long start, long end, TileStore.TileFeature feature, FeatureKey key, FillReader reader) {}

	private static final class ActiveFill {
		final TileStore.TileFeature feature;
		int count;

		ActiveFill(TileStore.TileFeature feature, int count) {
			this.feature = feature;
			this.count = count;
		}
	}

	private static final class FillReader implements Closeable {
		private final DataInputStream in;
		private final TileStore.TileFeature feature;
		private final FeatureKey key;
		private int remainingRuns;
		private long previousEnd;
		private long runStart;
		private long runEnd;

		FillReader(Path path) throws IOException {
			in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path), 1 << 13));
			int layer = TileStore.readVarInt(in);
			long order = TileStore.readVarLong(in);
			int attrs = TileStore.readVarInt(in);
			var type = TileGeometryCodec.GeometryType.values()[in.readUnsignedByte()];
			int length = TileStore.readVarInt(in);
			var commands = new int[length];
			for(int i=0; i<length; i++)
				commands[i] = TileStore.zigZagDecode(TileStore.readVarInt(in));
			feature = new TileStore.TileFeature(layer, order, attrs, type, commands, true);
			key = new FeatureKey(layer, order, attrs);
			remainingRuns = TileStore.readVarInt(in);
		}

		boolean loadNextRun() throws IOException {
			if(remainingRuns <= 0)
				return false;
			remainingRuns--;
			runStart = previousEnd + TileStore.readVarLong(in);
			runEnd = runStart + TileStore.readVarLong(in);
			previousEnd = runEnd;
			return true;
		}

		FillRun run() {
			return new FillRun(runStart, runEnd, feature, key, this);
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
