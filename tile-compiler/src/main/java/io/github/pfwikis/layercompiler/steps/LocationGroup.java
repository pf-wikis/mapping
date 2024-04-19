package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.model.Geometry.Point;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocationGroup extends LCStep {
	
	@Override
	public LCContent process() throws IOException {
		var col = this.getInput().toFeatureCollection();

		for(var feature : col.getFeatures()) {
			var loc = ((Point)feature.getGeometry()).getCoordinates();
			double zoom = 1;
			double zoomPart = 0.5/Math.PI*Math.pow(2, zoom);
			double x = zoomPart*(Math.PI+Math.toRadians(loc.lng()));
			double y = zoomPart*(Math.PI-Math.log(Math.tan(Math.PI/4+Math.toRadians(loc.lat())/2)));
			log.info("{} =>  {};{}", loc, x, y);
		}
		
		return LCContent.from(col);
	}
}
