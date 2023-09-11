package io.github.pfwikis.layercompiler.steps.rivers;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.layercompiler.steps.LCStep;
import mil.nga.sf.Point;
import mil.nga.sf.geojson.FeatureCollection;
import mil.nga.sf.geojson.Polygon;

public class RemovePointsNotOnLand extends LCStep {

	@Override
	public byte[] process() throws Exception {
		var landPoints = PointsOnLandSelector
				.collectLandPoints(ctx, getInput(), getInput("land"));
		
		var featureCol = new ObjectMapper().readValue(getInput(), FeatureCollection.class);
		for (var feature : featureCol.getFeatures()) {
            if (feature.getGeometry() instanceof Polygon pol) {
            	for(var line : pol.getRings()) {
	                var points = line.getLineString().getPoints();
	                
	                reducePoints(points, landPoints);
	                line.setLineString(new mil.nga.sf.LineString(points));
            	}
            } else {
                throw new IllegalStateException("Unhandled type " + feature.getGeometryType());
            }
        }
		return new ObjectMapper().writeValueAsBytes(featureCol);
	}

	private void reducePoints(List<Point> points, Set<Point> landPoints) {
		for(int i=0;i<points.size();i++) {
			if(test(points, landPoints, i)) {
				points.remove((i+1)%points.size());
				i--;
			}
		}
	}
	
	private boolean test(List<Point> points, Set<Point> landPoints, int i) {
		int a=i;
		int b=(i+1)%points.size();
		int c=(i+2)%points.size();
		
		return !landPoints.contains(points.get(a))
			&& !landPoints.contains(points.get(b))
			&& !landPoints.contains(points.get(c));
	}

}
