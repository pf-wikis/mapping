package io.github.pfwikis.layercompiler.steps.rivers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.layercompiler.steps.model.LCStep.Ctx;
import io.github.pfwikis.run.Tools;
import mil.nga.sf.Point;
import mil.nga.sf.geojson.Geometry;
import mil.nga.sf.geojson.LineString;
import mil.nga.sf.geojson.Polygon;

public class PointsOnLandSelector {

	public static Set<Point> collectLandPoints(LCStep step, Ctx ctx, LCContent input, LCContent land) throws IOException {
		var clipped = Tools
            .mapshaper(
            	step,
            	input,
                "-clip", land,
                "-explode"
            );
		var featureCol = clipped.toNgaFeatureCollection();
		clipped.finishUsage();
		var result = new HashSet<Point>();
		for (var feature : featureCol.getFeatures()) {
			collect(feature.getGeometry(), result);
        }
		return result;
	}

	private static void collect(Geometry feature, HashSet<Point> result) {
		if(feature == null) {
			//noop
		} else if (feature instanceof LineString line) {
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
