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
			int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
			int windowSize = Math.max(64, threads * 8);
			var executor = Executors.newFixedThreadPool(threads);
			var window = new ArrayDeque<TileWork>();
			long encodedCount = 0;
			start = System.nanoTime();
			try(var merger = new TileMerger(runs, store.fillRuns());
				var writer = new PmtilesWriter(output);
				var signatures = new TileSignatureTracker(tempDir.resolve("signatures"))) {
				while(merger.nextRun()) {
					var tileFeatures = merger.features();
					long tileId = merger.runTileId();
					int count = merger.runCount();
					long[] kept = signatures.keptRanges(tileId, count, tileFeatures);
					if(kept != null && kept.length == 0)
						continue;
					var future = executor.submit(() -> encodeTile(encoder, context, tileFeatures, fillCache));
					window.addLast(new TileWork(tileId, count, kept, future));
					if(window.size() >= windowSize)
						encodedCount += writeNext(window, writer);
				}
				while(!window.isEmpty())
					encodedCount += writeNext(window, writer);
				log.info("Encoded {} tiles in {}ms", encodedCount, (System.nanoTime()-start)/1_000_000);

				start = System.nanoTime();
				writer.finish(maxZoom, context.fields());
				log.info("Wrote PMTiles in {}ms", (System.nanoTime()-start)/1_000_000);
			} finally {
				executor.shutdown();
				executor.awaitTermination(1, TimeUnit.MINUTES);
			}
		} finally {
			deleteRecursively(tempDir);
		}
	}

	private static int writeNext(ArrayDeque<TileWork> window, PmtilesWriter writer) throws IOException, InterruptedException, ExecutionException {
		var work = window.removeFirst();
		var tile = work.bytes().get();
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
	}

	private static EncodedTile encodeTile(MltTileEncoder encoder, TileContext context, List<TileStore.TileFeature> features,
		java.util.concurrent.ConcurrentHashMap<List<FillKey>, EncodedTile> fillCache) throws IOException, GeometryException {
		// High-zoom fills cover most of the world; their MLT bytes only depend on
		// the set of fill layers/attributes, so encode each combination once.
		var key = fillKey(features);
		if(key != null) {
			var cached = fillCache.get(key);
			if(cached != null)
				return cached;
			var bytes = encoder.encode(toMlt(context, features));
			var tile = new EncodedTile(bytes, PmtilesWriter.hash(bytes));
			var previous = fillCache.putIfAbsent(key, tile);
			return previous == null ? tile : previous;
		}
		return new EncodedTile(encoder.encode(toMlt(context, features)), 0L);
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
