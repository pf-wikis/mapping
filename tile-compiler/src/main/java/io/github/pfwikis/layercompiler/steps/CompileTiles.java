package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeometryPipeline;
import com.onthegomap.planetiler.reader.SourceFeature;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.layercompiler.steps.time.TimeMetaCollect.TimeMeta;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class CompileTiles extends StepExecutor {
	
	private String filename = "golarion";
	private String extension = "pmtiles";
	
    @Override
    public Content process(Inputs in) throws Exception {
    	log.info("Compiling Tiles");
    	
    	var meta = in.getInput("time-meta").toFeatureCollection().getProperties().getTimeMeta();
    	Objects.requireNonNull(meta);

    	int maxZoom = Ctx.INSTANCE.getOptions().getMaxZoom();
    	var planetiler = Planetiler.create(Arguments.of(
    		"tile-format", "mlt",
    		"maxzoom", Integer.toString(maxZoom),
    		"render_maxzoom", Integer.toString(maxZoom),
    		//not yet supported
    		//"mlt_advanced", "true",
    		//"mlt_tessellate_polygons", "true",
    		"mlt_shared_dict", "true",
    		"exclude_ids", "true",
    		"force", "true",
    		//increase detail at max zoom for best overzooming
    		"min_feature_size_at_max_zoom", "0",
    		"simplify_tolerance_at_max_zoom", "0"
    	));
    	in.getInputs().entrySet()
			.stream()
			.filter(l->!l.getKey().equals("time-meta"))
			.map(e->Pair.of(e.getKey(), applyTimeMeta(meta, e.getValue().toFeatureCollection())))
			.peek(e->cleanProperties(e.getValue()))
			.forEach(e->planetiler.addGeoJsonSource(e.getKey(), GeoData.from(e.getValue()).toTmpFile(this)));
    	planetiler.setOutput(new File(Ctx.INSTANCE.getOptions().targetDirectory(), filename+"."+extension).toPath());
    	planetiler.setProfile(new Profile() {

			@Override
			public void processFeature(SourceFeature f, FeatureCollector features) {
				//sourceFeature.latLonGeometry()
				var out = features.anyGeometry(f.getSource())
					.putAttrs(f.tags());
				//filter by zoom level
				out.setZoomRange(
					max(
						getInt(f, Properties.Fields.tileMinzoom),
						clamp(0, getInt(f, Properties.Fields.filterMinzoom), maxZoom),
						0
					),
					min(
						getInt(f, Properties.Fields.filterMaxzoom),
						getInt(f, Properties.Fields.tileMaxzoom),
						maxZoom
					)
				);
				
				//disable simplifaction completely at maxzoom
				out.transformScaledGeometryByZoom(zoom-> {
					if(maxZoom == zoom) {
						return GeometryPipeline.NOOP;
					}
					return null;
				});
			}
    	});
    	planetiler.run();

        return Content.empty();
    }
    
    
    private FeatureCollection applyTimeMeta(TimeMeta meta, FeatureCollection fc) {
    	fc.getFeatures().forEach(f-> {
    		var time = f.getProperties().getTime();
    		f.getProperties().setTimeIndexStart(meta.getIndexForStart(time));
    		f.getProperties().setTimeIndexEnd(meta.getIndexForEnd(time));
    		f.getProperties().setTime(null);
    	});
    	return fc;
	}


	private void cleanProperties(FeatureCollection fc) {
    	for(var f:fc.getFeatures()) {
    		f.getProperties().setTime(null);
    	}
	}
	
	private Integer getInt(SourceFeature f, String field) {
		var val = f.getTag(field);
		if(val == null) return null;
		if(val instanceof Number n) return n.intValue();
		return Integer.parseInt(val.toString());
	}

    private static int max(Integer... values) {
    	Integer max = null;
		for(var v:values) {
			if(v != null && (max == null || max < v))
				max = v;
		}
		return max;
	}
    
    private static int min(Integer... values) {
		Integer min = null;
		for(var v:values) {
			if(v != null && (min == null || min > v))
				min = v;
		}
		return min;
	}
    
    private static Integer clamp(int min, Integer value, int max) {
		if(value == null) return null;
		return Math.clamp(value, min, max);
	}

}
