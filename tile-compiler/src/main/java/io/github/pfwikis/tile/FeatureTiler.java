package io.github.pfwikis.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;

import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.model.Properties.ExportProperties;
import io.github.pfwikis.util.Jackson;

/**
 * Turns the prepared GeoJSON layers into compact tile features. Features are
 * written to a {@link TileStore} which spills sorted runs to disk, so the tiler
 * never has to keep the (potentially enormous) final tile set in memory.
 */
final class FeatureTiler {
	private static final double BUFFER_PIXELS = 4d;
	private static final double MIN_FEATURE_SIZE = 0.1d;
	private static final double SIMPLIFY_TOLERANCE = 0.1d;
	private static final TileGeometryCodec.EncodedGeometry FILL =
		TileGeometryCodec.fill((int)Math.round(4096d * BUFFER_PIXELS / 256d));

	private final int maxZoom;
	private final TileExtents extents;
	private final TileStore store;
	private final TileContext context;

	FeatureTiler(int maxZoom, TileStore store, TileContext context) {
		this.maxZoom = maxZoom;
		this.extents = new TileExtents(maxZoom);
		this.store = store;
		this.context = context;
	}

	void tile(Map<String, FeatureCollection> layers) {
		// Use a dedicated pool instead of the common pool: the common pool only has
		// availableProcessors-1 workers, which leaves one core idle when tiling is
		// the only thing running. Nested parallel streams inside the tasks reuse
		// this pool because they are invoked from its workers.
		int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
		try(var pool = new java.util.concurrent.ForkJoinPool(parallelism)) {
			pool.submit(() -> tileLayers(layers)).join();
		}
	}

	private void tileLayers(Map<String, FeatureCollection> layers) {
		// Tile one layer at a time so the prepared source geometry of a finished
		// layer can be released before the next (usually larger) layer is tiled.
		for(var entry : layers.entrySet()) {
			int layerId = context.layerId(entry.getKey());
			var features = entry.getValue().getFeatures();
			var refs = new ArrayList<FeatureRef>(features.size());
			long index = 0;
			for(var feature : features)
				refs.add(new FeatureRef(layerId, entry.getKey(), feature, index++));
			refs.parallelStream().forEach(ref -> {
				try {
					tileFeature(ref);
				} catch(Exception e) {
					throw new IllegalStateException("Could not tile "+ref.layerName()+" feature "+ref.feature(), e);
				}
			});
			features.clear();
		}
		layers.clear();
	}

	private record FeatureRef(int layerId, String layerName, Feature feature, long order) {}

	private void tileFeature(FeatureRef ref) throws Exception {
		var feature = ref.feature();
		if(feature.getGeometry() == null || feature.getProperties() == null)
			return;

		var properties = feature.getProperties();
		int minZoom = 0;
		int featureMaxZoom = maxZoom;
		ExportProperties export = properties.getExport();
		if(export != null) {
			minZoom = Math.max(0, Math.min(maxZoom, export.getTileMinzoom()));
			featureMaxZoom = Math.max(0, Math.min(maxZoom, export.getTileMaxzoom()));
		}
		if(minZoom > featureMaxZoom)
			return;

		var propertyJson = Jackson.JSON.valueToTree(properties);
		var attrs = attributes(propertyJson);
		int labelMinZoom = -1;
		Object labelValue = null;
		if("locations".equals(ref.layerName()) && attrs.remove(Properties.Fields.label) != null) {
			labelValue = javaValue(propertyJson.get(Properties.Fields.label));
			int pregroup = properties.getPregroupMinzoom() == null ? 0 : properties.getPregroupMinzoom();
			labelMinZoom = Math.min(maxZoom, pregroup+5);
		}

		Geometry world = GeoUtils.sortPolygonsByAreaDescending(
			GeoUtils.latLonToWorldCoords(ModelGeometry.toJts(feature.getGeometry()))
		);
		boolean area = world instanceof Polygon || world instanceof org.locationtech.jts.geom.MultiPolygon;
		boolean point = world instanceof Point || world instanceof MultiPoint;

		for(int zoom = featureMaxZoom; zoom >= minZoom; zoom--) {
			Map<String, Object> attrsAtZoom = attrs;
			if(labelMinZoom >= 0 && zoom >= labelMinZoom) {
				var withLabel = new TreeMap<>(attrs);
				withLabel.put(Properties.Fields.label, labelValue);
				attrsAtZoom = withLabel;
			}
			int attrsId = context.attrsId(attrsAtZoom);
			context.addFields(ref.layerName(), attrsId, attrsAtZoom);

			double scale = 1 << zoom;
			Geometry scaled = AffineTransformation.scaleInstance(scale, scale).transform(world);
			double minSize = (zoom == maxZoom ? 0 : MIN_FEATURE_SIZE) / 256d;
			double tolerance = (zoom == maxZoom ? 0 : SIMPLIFY_TOLERANCE) / 256d;
			double buffer = BUFFER_PIXELS / 256d;

			if(!point && tolerance > 0 && !scaled.isEmpty())
				scaled = DouglasPeuckerSimplifier.simplify(scaled, tolerance);
			if(scaled.isEmpty())
				continue;

			if(point) {
				tilePoint(ref, scaled, zoom, attrsId, buffer);
			} else {
				tileLineOrPolygon(ref, scaled, zoom, attrsId, buffer, minSize, area);
			}
		}
	}

	private void tilePoint(FeatureRef ref, Geometry geometry, int zoom, int attrsId, double buffer) throws GeometryException {
		var tiled = TiledGeometry.slicePointsIntoTiles(extents.getForZoom(zoom), buffer, zoom, geometry.getCoordinates());
		var entries = tiled.getTileData().entrySet();
		if(entries.size() >= 128) {
			entries.parallelStream().forEach(entry -> {
				try {
					addPointTile(ref, entry, attrsId);
				} catch(GeometryException e) {
					throw new IllegalStateException(e);
				}
			});
		} else {
			for(var entry : entries)
				addPointTile(ref, entry, attrsId);
		}
	}

	private void addPointTile(FeatureRef ref, Map.Entry<TileCoord, List<List<org.locationtech.jts.geom.CoordinateSequence>>> entry, int attrsId) throws GeometryException {
		var geom = GeometryCoordinateSequences.reassemblePoints(entry.getValue());
		if(geom.isEmpty())
			return;
		var encoded = TileGeometryCodec.encodeToCommands(geom, 0);
		store.add(entry.getKey().hilbertEncoded(),
			new TileStore.TileFeature(ref.layerId(), ref.order(), attrsId, encoded.type(), encoded.commands(), false));
	}

	private void tileLineOrPolygon(FeatureRef ref, Geometry geometry, int zoom, int attrsId, double buffer, double minSize, boolean area) throws GeometryException {
		var groups = GeometryCoordinateSequences.extractGroups(geometry, area ? minSize*minSize : minSize);
		if(groups.isEmpty())
			return;
		var tiled = TiledGeometry.sliceIntoTiles(groups, buffer, area, zoom, extents.getForZoom(zoom));
		int coordinateScale = area ? 0 : Math.min(17, Math.max(maxZoom, 14) - zoom);

		var entries = tiled.getTileData().entrySet();
		if(entries.size() >= 128) {
			entries.parallelStream().forEach(entry -> {
				try {
					addLineOrPolygonTile(ref, entry, attrsId, area, coordinateScale);
				} catch(GeometryException e) {
					throw new IllegalStateException(e);
				}
			});
		} else {
			for(var entry : entries)
				addLineOrPolygonTile(ref, entry, attrsId, area, coordinateScale);
		}

		if(area) {
			var runs = FillRuns.collect(tiled.filledRanges(), tiled.boundaryRanges(), zoom);
			if(runs.length > 0) {
				var fillFeature = new TileStore.TileFeature(ref.layerId(), ref.order(), attrsId, FILL.type(), FILL.commands(), true);
				store.addFillChunk(fillFeature, runs);
			}
		}
	}

	private void addLineOrPolygonTile(FeatureRef ref, Map.Entry<TileCoord, List<List<org.locationtech.jts.geom.CoordinateSequence>>> entry, int attrsId, boolean area, int coordinateScale) throws GeometryException {
		Geometry geom;
		if(area) {
			geom = GeometryCoordinateSequences.reassemblePolygons(entry.getValue());
			geom = GeoUtils.snapAndFixPolygon(geom);
			geom = geom.reverse();
		} else {
			geom = GeometryCoordinateSequences.reassembleLineStrings(entry.getValue());
		}
		if(geom.isEmpty())
			return;
		var encoded = TileGeometryCodec.encodeToCommands(geom, coordinateScale);
		store.add(entry.getKey().hilbertEncoded(),
			new TileStore.TileFeature(ref.layerId(), ref.order(), attrsId, encoded.type(), encoded.commands(), false));
	}

	private static Map<String, Object> attributes(tools.jackson.databind.JsonNode json) {
		var result = new TreeMap<String, Object>();
		for(var entry : json.properties()) {
			if(Properties.Fields.export.equals(entry.getKey()))
				continue;
			result.put(entry.getKey(), javaValue(entry.getValue()));
		}
		return result;
	}

	private static Object javaValue(tools.jackson.databind.JsonNode node) {
		if(node == null || node.isNull())
			return null;
		if(node.isTextual())
			return node.textValue();
		if(node.isBoolean())
			return node.booleanValue();
		if(node.isInt())
			return node.intValue();
		if(node.isIntegralNumber())
			return node.longValue();
		if(node.isFloatingPointNumber()) {
			double value = node.doubleValue();
			if(value == Math.rint(value) && Math.abs(value) <= 2_147_483_647d)
				return (int)value;
			return value;
		}
		return node.toString();
	}
}
