package io.github.pfwikis.layercompiler.steps;

import java.util.ArrayList;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.MultiPolygon;
import io.github.pfwikis.model.Geometry.Polygon;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class Highlights extends StepExecutor {

	@Override
	public Content process(Inputs in) throws Exception {
		var fc = new FeatureCollection();
		for(var val:in.getInputs().values()) {
			fc.getFeatures().addAll(val.toFeatureCollection().getFeatures());
		}
		
		var measured = Tools.mapshaper(this, GeoData.from(fc),
			"-filter", "Boolean(label)",
			"-dissolve", "label"+in.getTimeState().mapshaperTimeFields(), "allow-overlaps",
			"-each", "buffer=Math.min(Math.sqrt(this.area)/40, 0.5)",
			"-each", "vertexCount=(this.geometry.type === 'Polygon' ? this.geometry.coordinates : this.geometry.coordinates.flat()).reduce((n, ring) => n + ring.length - 1, 0)",
			"-simplify", "Math.min(1,10000/vertexCount)", "variable", "keep-shapes",
			"-each", "this.properties.vertexCount=undefined"
		);
		
		List<Feature> todo = new ArrayList<>(measured.toFeatureCollection().getFeatures());
		long chunkSize = 100_000;
		log.info("Generating highlights for {} features of total size {} in chunks of {}",
				todo.size(),
				todo.stream().mapToLong(f->f.getGeometry().size()).sum(),
				chunkSize);
		
		//this chunking is necessary to prevent an OOM
		fc = new FeatureCollection();
		int chunk = 0;
		while(!todo.isEmpty()) {
			var chunkCol = new FeatureCollection();
			long currentSize = 0;
			while(currentSize < chunkSize && !todo.isEmpty()) {
				var next = todo.getFirst();
				chunkCol.getFeatures().add(next);
				todo = todo.subList(1, todo.size());
				currentSize+=next.getGeometry().size();
			}
			log.info("Chunk {} with {} features and size {} ({} still todo)",
					chunk++,
					chunkCol.getFeatures().size(),
					currentSize,
					todo.size()
			);
			
			var buffered = Tools.qgis(this, "native:buffer", GeoData.from(chunkCol),
	            "--DISTANCE=expression:5*\"buffer\"",
	            "--SEGMENTS=5",
	            "--END_CAP_STYLE=0",
	            "--JOIN_STYLE=0",
	            "--MITER_LIMIT=2"
	        );
			
			var debuffered = Tools.qgis(this, "native:buffer", buffered,
	            "--DISTANCE=expression:-4*\"buffer\"",
	            "--SEGMENTS=5",
	            "--END_CAP_STYLE=0",
	            "--JOIN_STYLE=0",
	            "--MITER_LIMIT=2"
	        );
			
			var simple = Tools.mapshaper(this, debuffered,
				"-each", "this.properties.buffer=undefined",
				"-simplify", "0.4", "keep-shapes"
			);
	        var smooth = Tools.qgis(this, "native:smoothgeometry", simple,
	            "--ITERATIONS=2",
	            "--OFFSET=0.3",
	            "--MAX_ANGLE=170"
	        );
	        fc.getFeatures().addAll(smooth.toFeatureCollection().getFeatures());
		}
		
		fc.getFeatures().removeIf(f->f.getGeometry() == null);
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
			new LngLat(-138,  90),
			new LngLat(-138, -90)
		));
		coordinates.forEach(line->res.add(line.reversed()));
		return res;
	}
}
