package io.github.pfwikis.layercompiler.steps;

import java.util.ArrayList;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.MultiPolygon;
import io.github.pfwikis.model.Geometry.Polygon;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;

public class Highlights extends LCStep {

	@Override
	public LCContent process(Inputs in) throws Exception {
		var fc = new FeatureCollection();
		for(var val:in.getInputs().values()) {
			fc.getFeatures().addAll(val.toFeatureCollection().getFeatures());
		}
		
		var dissolved = Tools.mapshaper(this, LCContent.from(fc), "-dissolve", "label");
		var buffered = Tools.qgis(this, "native:buffer", dissolved,
            "--DISTANCE=expression:sqrt($area)/25",
            "--SEGMENTS=20",
            "--END_CAP_STYLE=0",
            "--JOIN_STYLE=0",
            "--MITER_LIMIT=2"
        );
        var reduced = Tools.mapshaper(this, buffered, "-dissolve", "label", "-simplify", "percentage=0.6", "keep-shapes");
        var smooth = Tools.qgis(this, "native:smoothgeometry", reduced,
            "--ITERATIONS=3",
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
		
		return LCContent.from(fc);
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
