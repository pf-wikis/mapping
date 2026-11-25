package io.github.pfwikis.tile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.maplibre.mlt.data.MVTFeature;

import io.github.pfwikis.model.FeatureCollection;
import lombok.extern.slf4j.Slf4j;

/**
 * Drop-in replacement for the Planetiler tile-compilation step. It writes the same
 * PMTiles/MLT tiles without Planetiler's external feature sort and archive machinery.
 *
 * <p>The tile feature set can contain hundreds of millions of entries at high zoom
 * levels, so tiling streams into spill files that are merged back in tile-id order
 * while tiles are encoded and written. Memory use is therefore independent of the
 * output tile count.
 */
@Slf4j
public final class MltPmtilesCompiler {

	/** Counters for the encode loop; worker threads update the atomic fields. */
	private static final class EncodeStats {
		long runs;
		long droppedRuns;
		long mergeNanos;
		long signatureNanos;
		long waitNanos;
		long writeNanos;
		final AtomicLong encodeCalls = new AtomicLong();
		final AtomicLong fillCacheHits = new AtomicLong();
		final AtomicLong encodeNanos = new AtomicLong();
	}

	public void compile(Map<String, FeatureCollection> layers, int maxZoom, Path output) throws Exception {
		log.info("Tiling {} layers up to z{}", layers.size(), maxZoom);
		var tempDir = Files.createTempDirectory("pf-tile-store-");
		var context = new TileContext();
		long start = System.nanoTime();
		try(var store = new TileStore(tempDir)) {
			new FeatureTiler(maxZoom, store, context).tile(layers);
			var runs = store.finish();
			log.info("Spilled {} run(s) in {}ms", runs.size(), (System.nanoTime()-start)/1_000_000);

			var encoder = new MltTileEncoder();
			var fillCache = new java.util.concurrent.ConcurrentHashMap<List<FillKey>, EncodedTile>();
			int threads = Math.max(1, Integer.getInteger("tile.encodeThreads",
				Runtime.getRuntime().availableProcessors()));
			int windowSize = Math.max(64, threads * 8);
			var executor = Executors.newFixedThreadPool(threads);
			var stats = new EncodeStats();
			var window = new ArrayDeque<TileWork>();
			long encodedCount = 0;
			start = System.nanoTime();
			try(var merger = new TileMerger(runs, store.fillRuns());
				var writer = new PmtilesWriter(output);
				var signatures = new TileSignatureTracker(tempDir.resolve("signatures"), maxZoom)) {
				while(merger.nextRun()) {
					long phase = System.nanoTime();
					var tileFeatures = collapseUniformFills(merger.features());
					long tileId = merger.runTileId();
					int count = merger.runCount();
					stats.runs++;
					stats.mergeNanos += System.nanoTime() - phase;

					phase = System.nanoTime();
					long[] kept = signatures.keptRanges(tileId, count, tileFeatures);
					stats.signatureNanos += System.nanoTime() - phase;

					if(kept != null && kept.length == 0) {
						stats.droppedRuns++;
						continue;
					}
					var future = executor.submit(() -> encodeTile(encoder, context, tileFeatures, fillCache, stats));
					window.addLast(new TileWork(tileId, count, kept, future));
					if(window.size() >= windowSize)
						encodedCount += writeNext(window, writer, stats);
				}
				while(!window.isEmpty())
					encodedCount += writeNext(window, writer, stats);
				long loopNanos = System.nanoTime()-start;
				log.info("Encoded {} tiles in {}ms", encodedCount, loopNanos/1_000_000);
				log.info("Encode loop: {} runs ({} dropped), {} encode calls ({} fill-cache hits); "
						+ "merge={}ms signature={}ms wait={}ms write={}ms encodeBusy={}ms on {} threads ({}% of wall)",
					stats.runs, stats.droppedRuns, stats.encodeCalls.get(), stats.fillCacheHits.get(),
					stats.mergeNanos/1_000_000, stats.signatureNanos/1_000_000,
					stats.waitNanos/1_000_000, stats.writeNanos/1_000_000,
					stats.encodeNanos.get()/1_000_000, threads,
					loopNanos == 0 ? 0 : stats.encodeNanos.get() * 100L / (loopNanos * threads));

				start = System.nanoTime();
				writer.finish(maxZoom, context.fields());
				log.info("Wrote PMTiles in {}ms ({} entries, {} unique tile contents)",
					(System.nanoTime()-start)/1_000_000, writer.entryCount(), writer.uniqueContents());
			} finally {
				executor.shutdown();
				executor.awaitTermination(1, TimeUnit.MINUTES);
			}
		} finally {
			deleteRecursively(tempDir);
		}
	}

	/**
	 * A fill-only tile is covered completely by each of its fill features. All
	 * fills are opaque, so only the topmost fill is visible and the rest can be
	 * removed.
	 */
	static List<TileStore.TileFeature> collapseUniformFills(List<TileStore.TileFeature> features) {
		if(features.size() <= 1)
			return features;
		int layer = -1;
		TileStore.TileFeature top = null;
		for(var feature : features) {
			if(!feature.fill())
				return features;
			if(top == null) {
				layer = feature.layer();
				top = feature;
			} else {
				// Different source layers can be drawn in style-layer order, which
				// the compiler does not know. Only collapse within one layer.
				if(feature.layer() != layer)
					return features;
				if(feature.order() > top.order())
					top = feature;
			}
		}
		return List.of(top);
	}

	private static int writeNext(ArrayDeque<TileWork> window, PmtilesWriter writer, EncodeStats stats) throws IOException, InterruptedException, ExecutionException {
		var work = window.removeFirst();
		long phase = System.nanoTime();
		var tile = work.bytes().get();
		stats.waitNanos += System.nanoTime() - phase;
		phase = System.nanoTime();
		try {
			if(tile == null || tile.bytes() == null)
				return 0;
			if(work.kept() == null) {
				writer.addRun(work.tileId(), work.count(), tile.bytes(), tile.hash());
				return work.count();
			}

			int written = 0;
			for(int i=0; i<work.kept().length; i+=2) {
				int length = (int)work.kept()[i+1];
				writer.addRun(work.tileId() + work.kept()[i], length, tile.bytes(), tile.hash());
				written += length;
			}
			return written;
		} finally {
			stats.writeNanos += System.nanoTime() - phase;
		}
	}

	private static EncodedTile encodeTile(MltTileEncoder encoder, TileContext context, List<TileStore.TileFeature> features,
		java.util.concurrent.ConcurrentHashMap<List<FillKey>, EncodedTile> fillCache, EncodeStats stats) throws IOException, GeometryException {
		stats.encodeCalls.incrementAndGet();
		long start = System.nanoTime();
		try {
			// High-zoom fills cover most of the world; their MLT bytes only depend on
			// the set of fill layers/attributes, so encode each combination once.
			var key = fillKey(features);
			if(key != null) {
				var cached = fillCache.get(key);
				if(cached != null) {
					stats.fillCacheHits.incrementAndGet();
					return cached;
				}
				var bytes = encoder.encode(toMlt(context, features));
				var tile = new EncodedTile(bytes, PmtilesWriter.hash(bytes));
				var previous = fillCache.putIfAbsent(key, tile);
				return previous == null ? tile : previous;
			}
			return new EncodedTile(encoder.encode(toMlt(context, features)), 0L);
		} finally {
			stats.encodeNanos.addAndGet(System.nanoTime() - start);
		}
	}

	private static List<FillKey> fillKey(List<TileStore.TileFeature> features) {
		var key = new ArrayList<FillKey>(features.size());
		for(var feature : features) {
			if(!feature.fill())
				return null;
			key.add(new FillKey(feature.layer(), feature.order(), feature.attrs()));
		}
		return key;
	}

	private record FillKey(int layer, long order, int attrs) {}
	private record EncodedTile(byte[] bytes, long hash) {}

	private static Map<String, List<org.maplibre.mlt.data.Feature>> toMlt(TileContext context, List<TileStore.TileFeature> features) throws GeometryException {
		var result = new HashMap<String, List<org.maplibre.mlt.data.Feature>>();
		for(var feature : features) {
			var geometry = TileGeometryCodec.decode(
				new TileGeometryCodec.EncodedGeometry(feature.type(), feature.commands())
			);
			if(geometry.isEmpty())
				continue;
			var mltFeature = MVTFeature.builder()
				.geometry(geometry)
				.properties(context.attrs(feature.attrs()))
				.build();
			result.computeIfAbsent(context.layerName(feature.layer()), key -> new ArrayList<>()).add(mltFeature);
		}
		return result;
	}

	private record TileWork(long tileId, int count, long[] kept, Future<EncodedTile> bytes) {}

	private static void deleteRecursively(Path directory) {
		try(var files = Files.walk(directory)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch(IOException ignored) {
				}
			});
		} catch(IOException ignored) {
		}
	}
}
