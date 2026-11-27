package io.github.pfwikis.tile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.maplibre.mlt.converter.ConversionConfig;
import org.maplibre.mlt.converter.FeatureTableOptimizations;
import org.maplibre.mlt.converter.MltConverter;
import org.maplibre.mlt.converter.ColumnMapping;
import org.maplibre.mlt.converter.ColumnMappingConfig;
import org.maplibre.mlt.data.MapboxVectorTile;
import org.maplibre.mlt.data.Layer;
import org.maplibre.mlt.data.MVTFeature;
import org.maplibre.mlt.metadata.tileset.MltMetadata;

final class MltTileEncoder {
	private static final Pattern MATCH_ALL = Pattern.compile(".*");
	/**
	 * Pre-tessellation stores polygon triangle meshes so renderers can upload
	 * them directly instead of triangulating at runtime. It is disabled by
	 * default because MapLibre GL JS does not render the mesh yet: it rebuilds
	 * the outline rings and tessellates them again, so the extra mesh streams
	 * only cost build time and archive size. Re-enable by default as soon as
	 * MapLibre GL JS (or the WebGPU renderer) supports drawing pre-tessellated
	 * tiles; until then it remains available with {@code -Dtile.preTessellate=true}.
	 */
	private static final boolean PRE_TESSELLATE = Boolean.parseBoolean(System.getProperty("tile.preTessellate", "false"));

	byte[] encode(Map<String, List<org.maplibre.mlt.data.Feature>> tileLayers) throws IOException {
		if(tileLayers.isEmpty())
			return null;

		var layers = new ArrayList<Layer>();
		for(var entry : new TreeMap<>(tileLayers).entrySet()) {
			if(!entry.getValue().isEmpty())
				layers.add(new Layer(entry.getKey(), entry.getValue(), 4096));
		}
		if(layers.isEmpty())
			return null;

		var input = new MapboxVectorTile(layers);
		var stringColumns = new HashSet<String>();
		var stringColumnsByLayer = new HashMap<String, Set<String>>();
		findStringColumns(input, stringColumns, stringColumnsByLayer);

		var columnMappings = stringColumns.isEmpty()
			? new ColumnMappingConfig()
			: ColumnMappingConfig.of(MATCH_ALL, List.of(new ColumnMapping(stringColumns, true)));

		MltMetadata.TileSetMetadata metadata = MltConverter.createTilesetMetadata(
			input,
			columnMappings,
			false
		);

		var optimizations = input.getLayerStream().collect(Collectors.toMap(
			Layer::name,
			layer -> {
				var columns = stringColumnsByLayer.get(layer.name());
				return new FeatureTableOptimizations(
					false,
					true,
					columns == null || columns.isEmpty()
						? null
						: List.of(new ColumnMapping(columns, true))
				);
			}
		));

		var config = ConversionConfig.builder()
			.includeIds(false)
			.useFastPFOR(false)
			.useFSST(false)
			.typeMismatchPolicy(ConversionConfig.TypeMismatchPolicy.COERCE)
			.optimizations(optimizations)
			.preTessellatePolygons(PRE_TESSELLATE)
			.outlineFeatureTableNames(PRE_TESSELLATE ? List.of("ALL") : List.of())
			.useMortonEncoding(true)
			.build();

		return rawDeflate(MltConverter.encode(input, metadata, config, null));
	}

	private static void findStringColumns(MapboxVectorTile input, Set<String> stringColumns, Map<String, Set<String>> stringColumnsByLayer) {
		var valueCounts = new HashMap<String, Integer>();
		var notStringColumns = new HashSet<String>();
		int numRepeats = 0;
		boolean enoughRepeatedValues = false;

		for(Layer layer : input.getLayers()) {
			for(var feature : layer.features()) {
				var mvtFeature = (MVTFeature) feature;
				for(var entry : mvtFeature.getRawProperties().entrySet()) {
					Object value = entry.getValue();
					if(value instanceof String string) {
						int count = valueCounts.merge(string, 1, Integer::sum);
						if(!enoughRepeatedValues && count > 1) {
							numRepeats += count == 2 ? 2 : 1;
							if(numRepeats >= 50)
								enoughRepeatedValues = true;
						}
						stringColumns.add(entry.getKey());
						stringColumnsByLayer.computeIfAbsent(layer.name(), name -> new HashSet<>()).add(entry.getKey());
					} else {
						notStringColumns.add(entry.getKey());
					}
				}
			}
		}

		if(!enoughRepeatedValues) {
			stringColumns.clear();
			stringColumnsByLayer.clear();
		} else {
			stringColumns.removeAll(notStringColumns);
			stringColumnsByLayer.values().forEach(columns -> columns.removeAll(notStringColumns));
		}
	}

	/**
	 * Tile payloads use raw DEFLATE. The gzip header and trailer add 18 bytes to
	 * every unique tile without improving the compressed data, and the frontend
	 * supplies a custom PMTiles decompressor that inflates these payloads.
	 */
	private static byte[] rawDeflate(byte[] input) throws IOException {
		var output = new ByteArrayOutputStream(input.length);
		var deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		try(var deflate = new DeflaterOutputStream(output, deflater, 8192, true)) {
			deflate.write(input);
		} finally {
			deflater.end();
		}
		return output.toByteArray();
	}
}
