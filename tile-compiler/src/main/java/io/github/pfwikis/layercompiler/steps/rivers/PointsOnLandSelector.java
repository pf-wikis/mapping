package io.github.pfwikis.layercompiler.steps.rivers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.layercompiler.steps.model.LCStep.Ctx;
import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.Geometry.LineString;
import io.github.pfwikis.model.Geometry.Polygon;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;

public class PointsOnLandSelector {

	public static Set<LngLat> collectLandPoints(LCStep step, Ctx ctx, LCContent input, LCContent land) throws IOException {
		var clipped = Tools
            .mapshaper(
            	step,
            	input,
                "-clip", land,
                "-explode"
            );
		var featureCol = clipped.toFeatureCollection();
		clipped.finishUsage();
		var result = new HashSet<LngLat>();
		for (var feature : featureCol.getFeatures()) {
			collect(feature.getGeometry(), result);
        }
		return result;
	}

	private static void collect(Geometry feature, HashSet<LngLat> result) {
		if(feature == null) {
			//noop
		} else if (feature instanceof LineString line) {
            var points = line.getCoordinates();
            result.addAll(points);
        } else if (feature instanceof Polygon pol) {
        	for(var ring:pol.getCoordinates()) {
        		result.addAll(ring);
        	}
        } else {
            throw new IllegalStateException("Unhandled type " + feature.getClass().getName());
        }
	}
}
