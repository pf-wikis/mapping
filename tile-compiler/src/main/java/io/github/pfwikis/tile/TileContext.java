package io.github.pfwikis.tile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared dictionaries for tile features: layer names, attribute maps and the
 * union of fields per layer used for the PMTiles metadata.
 */
final class TileContext {
	private final ConcurrentHashMap<String, Integer> layerIds = new ConcurrentHashMap<>();
	private final List<String> layerNames = new ArrayList<>();
	private final ConcurrentHashMap<Map<String, Object>, Integer> attrIds = new ConcurrentHashMap<>();
	private final List<Map<String, Object>> attrs = new ArrayList<>();
	private final ConcurrentHashMap<String, Set<String>> fields = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Set<Integer>> seenAttrIds = new ConcurrentHashMap<>();

	int layerId(String name) {
		var id = layerIds.get(name);
		if(id != null)
			return id;
		synchronized(layerIds) {
			id = layerIds.get(name);
			if(id == null) {
				id = layerNames.size();
				layerNames.add(name);
				layerIds.put(name, id);
			}
			return id;
		}
	}

	String layerName(int id) {
		return layerNames.get(id);
	}

	int attrsId(Map<String, Object> map) {
		var id = attrIds.get(map);
		if(id != null)
			return id;
		synchronized(attrIds) {
			id = attrIds.get(map);
			if(id == null) {
				id = attrs.size();
				attrs.add(map);
				attrIds.put(map, id);
			}
			return id;
		}
	}

	Map<String, Object> attrs(int id) {
		return attrs.get(id);
	}

	void addFields(String layer, int attrsId, Map<String, Object> attrs) {
		if(seenAttrIds.computeIfAbsent(layer, k -> ConcurrentHashMap.newKeySet()).add(attrsId))
			fields.computeIfAbsent(layer, k -> ConcurrentHashMap.newKeySet()).addAll(attrs.keySet());
	}

	Map<String, Set<String>> fields() {
		return fields;
	}
}
