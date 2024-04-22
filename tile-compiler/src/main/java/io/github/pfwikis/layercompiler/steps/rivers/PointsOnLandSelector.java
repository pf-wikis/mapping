package io.github.pfwikis.layercompiler.steps.rivers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep.Ctx;
import io.github.pfwikis.run.Tools;
import mil.nga.sf.geojson.FeatureCollection;
import mil.nga.sf.geojson.Geometry;
import mil.nga.sf.geojson.LineString;
import mil.nga.sf.geojson.Polygon;
import mil.nga.sf.Point;

public class PointsOnLandSelector {

	public static Set<Point> collectLandPoints(Ctx ctx, LCContent input, LCContent land) throws IOException {
		var clipped = Tools
            .mapshaper(
            	input,
                "-clip", land,
                "-explode"
            );
		var featureCol = clipped.toNgaFeatureCollection();
		var result = new HashSet<Point>();
		for (var feature : featureCol.getFeatures()) {
			collect(feature.getGeometry(), result);
        }
		return result;
	}

	private static void collect(Geometry feature, HashSet<Point> result) {
		if (feature instanceof LineString line) {
            var points = line.getLineString().getPoints();
            result.addAll(points);
        } else if (feature instanceof Polygon pol) {
        	for(var ring:pol.getRings()) {
        		collect(ring, result);
        	}
        } else {
            throw new IllegalStateException("Unhandled type " + feature.getGeometryType());
        }
	}
}
