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

import org.apache.commons.io.FileUtils;
import org.maplibre.mlt.data.MVTFeature;

import com.google.common.base.Stopwatch;

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
		var spillWatch = Stopwatch.createStarted();
		try(var store = new TileStore(tempDir)) {
			new FeatureTiler(maxZoom, store, context).tile(layers);
			var runs = store.finish();
			log.info("Spilled {} run(s) in {}ms", runs.size(), spillWatch.elapsed(TimeUnit.MILLISECONDS));

			var encoder = new MltTileEncoder();
			var fillCache = new java.util.concurrent.ConcurrentHashMap<List<FillKey>, EncodedTile>();
			int threads = Math.max(1, Integer.getInteger("tile.encodeThreads",
				Runtime.getRuntime().availableProcessors()));
			int windowSize = Math.max(64, threads * 8);
			var executor = Executors.newFixedThreadPool(threads);
			var stats = new EncodeStats();
			var window = new ArrayDeque<TileWork>();
			long encodedCount = 0;
			var encodeWatch = Stopwatch.createStarted();
			var phase = Stopwatch.createUnstarted();
			try(var merger = new TileMerger(runs, store.fillRuns());
				var writer = new PmtilesWriter(output);
				var signatures = new TileSignatureTracker(tempDir.resolve("signatures"), maxZoom)) {
				while(merger.nextRun()) {
					phase.reset().start();
					var tileFeatures = collapseUniformFills(merger.features());
					long tileId = merger.runTileId();
					int count = merger.runCount();
					stats.runs++;
					stats.mergeNanos += phase.elapsed(TimeUnit.NANOSECONDS);

					phase.reset().start();
					long[] kept = signatures.keptRanges(tileId, count, tileFeatures);
					stats.signatureNanos += phase.elapsed(TimeUnit.NANOSECONDS);

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
				long loopNanos = encodeWatch.stop().elapsed(TimeUnit.NANOSECONDS);
				log.info("Encoded {} tiles in {}ms", encodedCount, loopNanos/1_000_000);
				log.info("Encode loop: {} runs ({} dropped), {} encode calls ({} fill-cache hits); "
						+ "merge={}ms signature={}ms wait={}ms write={}ms encodeBusy={}ms on {} threads ({}% of wall)",
					stats.runs, stats.droppedRuns, stats.encodeCalls.get(), stats.fillCacheHits.get(),
					stats.mergeNanos/1_000_000, stats.signatureNanos/1_000_000,
					stats.waitNanos/1_000_000, stats.writeNanos/1_000_000,
					stats.encodeNanos.get()/1_000_000, threads,
					loopNanos == 0 ? 0 : stats.encodeNanos.get() * 100L / (loopNanos * threads));

				var drops = signatures.dropStatistics();
				log.info("Tile dropping{}: kept={} dropped={}; dropped with dropped parent={} ({}%), "
						+ "with any dropped ancestor={} ({}%), with kept child={} ({}%)",
					drops.enabled() ? "" : " (disabled)",
					drops.keptTiles(), drops.droppedTiles(),
					drops.droppedWithDroppedParent(), percent(drops.droppedWithDroppedParent(), drops.droppedTiles()),
					drops.droppedWithDroppedAncestor(), percent(drops.droppedWithDroppedAncestor(), drops.droppedTiles()),
					drops.droppedWithKeptChild(), percent(drops.droppedWithKeptChild(), drops.droppedTiles()));

				var sig = signatures.signatureTimings();
				long sigParts = sig.analyzeNanos() + sig.computeNanos() + sig.transformNanos()
					+ sig.writeNanos() + sig.statsNanos();
				log.info("Signature breakdown: analyze={}ms ({} runs), compute={}ms ({} runs), "
						+ "transform={}ms, write={}ms ({} runs), stats={}ms, other={}ms",
					sig.analyzeNanos()/1_000_000, sig.analyzedRuns(),
					sig.computeNanos()/1_000_000, sig.comparedRuns(),
					sig.transformNanos()/1_000_000,
					sig.writeNanos()/1_000_000, sig.writtenRuns(),
					sig.statsNanos()/1_000_000,
					Math.max(0, stats.signatureNanos - sigParts)/1_000_000);
				log.info("Compute breakdown: points={}ms ({} calls), boundaries={}ms ({} calls), span/other={}ms",
					sig.pointsNanos()/1_000_000, sig.pointCompareCalls(),
					sig.boundariesNanos()/1_000_000, sig.boundaryCompareCalls(),
					Math.max(0, sig.computeNanos() - sig.pointsNanos() - sig.boundariesNanos())/1_000_000);

				var finishWatch = Stopwatch.createStarted();
				writer.finish(maxZoom, context.fields());
				log.info("Wrote PMTiles in {}ms ({} entries, {} unique tile contents)",
					finishWatch.elapsed(TimeUnit.MILLISECONDS), writer.entryCount(), writer.uniqueContents());
			} finally {
				executor.shutdown();
				executor.awaitTermination(1, TimeUnit.MINUTES);
			}
		} finally {
			FileUtils.deleteQuietly(tempDir.toFile());
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
					return features; //TODO this could be better
				if(feature.order() > top.order())
					top = feature;
			}
		}
		return List.of(top);
	}

	private static int writeNext(ArrayDeque<TileWork> window, PmtilesWriter writer, EncodeStats stats) throws IOException, InterruptedException, ExecutionException {
		var work = window.removeFirst();
		var waitWatch = Stopwatch.createStarted();
		var tile = work.bytes().get();
		stats.waitNanos += waitWatch.elapsed(TimeUnit.NANOSECONDS);
		var writeWatch = Stopwatch.createStarted();
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
			stats.writeNanos += writeWatch.elapsed(TimeUnit.NANOSECONDS);
		}
	}

	private static EncodedTile encodeTile(MltTileEncoder encoder, TileContext context, List<TileStore.TileFeature> features,
		java.util.concurrent.ConcurrentHashMap<List<FillKey>, EncodedTile> fillCache, EncodeStats stats) throws IOException, GeometryException {
		stats.encodeCalls.incrementAndGet();
		var watch = Stopwatch.createStarted();
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
			stats.encodeNanos.addAndGet(watch.elapsed(TimeUnit.NANOSECONDS));
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

	private static long percent(long part, long total) {
		return total == 0 ? 0 : part * 100 / total;
	}
}
