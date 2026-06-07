package io.github.pfwikis.layercompiler.steps;

import java.util.ArrayList;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.MultiPolygon;
import io.github.pfwikis.model.Geometry.Polygon;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.ANY)
public class Highlights extends StepExecutor {

	@Override
	public Content process(Inputs in) throws Exception {
		var fc = new FeatureCollection();
		for(var val:in.getInputs().values()) {
			fc.getFeatures().addAll(val.toFeatureCollection().getFeatures());
		}
		
		var dissolved = Tools.mapshaper(this, GeoData.from(fc), "-dissolve", "label"+in.getTimeState().mapshaperTimeFields());
		var buffered = Tools.qgis(this, "native:buffer", dissolved,
            "--DISTANCE=expression:sqrt($area)/25",
            "--SEGMENTS=20",
            "--END_CAP_STYLE=0",
            "--JOIN_STYLE=0",
            "--MITER_LIMIT=2"
        );
        var reduced = Tools.mapshaper2(this, buffered, buffered,
        	"combine-files",
        	"-dissolve", "label"+in.getTimeState().mapshaperTimeFields(),
        	"-simplify", "target=1", "visvalingam", "percentage=0.1", "keep-shapes",
        	"-simplify", "target=2", "visvalingam", "percentage=0.8", "keep-shapes",
        	"-merge-layers",
        	"-dissolve", "label"
        );
        var smooth = Tools.qgis(this, "native:smoothgeometry", reduced,
            "--ITERATIONS=2",
            "--OFFSET=0.3",
            "--MAX_ANGLE=180"
        );
		
		fc = smooth.toFeatureCollection();
		fc.getFeatures().forEach(f-> {
			if(f.getGeometry() instanceof Polygon p) {
				var res = new Polygon();
				res.setCoordinates(invert(p.getCoordinates()));
				f.setGeometry(res);
			}
			else if(f.getGeometry() instanceof MultiPolygon mp) {
				var res = new Polygon();
				res.setCoordinates(invert(mp.getCoordinates().stream().flatMap(List::stream).toList()));
				f.setGeometry(res);
			}
			else
				throw new IllegalStateException("Unknown layer type "+f.getGeometry().getClass().getSimpleName());
		});
		
		return Content.derivedFrom(in, GeoData.from(fc));
	}

	private List<List<LngLat>> invert(List<List<LngLat>> coordinates) {
		var res = new ArrayList<List<LngLat>>();
		res.add(List.of(
			new LngLat(-138, -90),
			new LngLat( 222, -90),
			new LngLat( 222,  90),
			new LngLat(-138,  90)
		));
		coordinates.forEach(line->res.add(line.reversed()));
		return res;
	}
}
