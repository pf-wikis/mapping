package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.lang3.tuple.Pair;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeometryPipeline;
import com.onthegomap.planetiler.reader.SourceFeature;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.model.Properties.ExportProperties;
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
    		"feature_source_id_multiplier", 1,
    		//"mlt_advanced", "true",
    		//does not work without outlines
    		//"mlt_tessellate_polygons", "true",
    		"mlt_shared_dict", "true",
    		"force", "true",
    		"exclude_ids", "true",
    		//increase detail at max zoom for best overzooming
    		"min_feature_size_at_max_zoom", "0",
    		"simplify_tolerance_at_max_zoom", "0"
    	));
    	in.getInputs().entrySet()
			.stream()
			.filter(l->!l.getKey().equals("time-meta"))
			.map(e->Pair.of(e.getKey(), shift(e.getValue().toFeatureCollection())))
			.forEach(e->planetiler.addGeoJsonSource(e.getKey(), GeoData.from(e.getValue()).toTmpFile(this)));
    	planetiler.setOutput(new File(Ctx.INSTANCE.getOptions().targetDirectory(), filename+"."+extension).toPath());
    	planetiler.setProfile(new Profile() {

			@Override
			public void processFeature(SourceFeature f, FeatureCollector features) {
				//sourceFeature.latLonGeometry()
				var out = features.anyGeometry(f.getSource());
				
				//filter by zoom level
				out.setZoomRange(
					f.getStruct(Properties.Fields.export).get(ExportProperties.Fields.tileMinzoom).orElse(0).asInt(),
					f.getStruct(Properties.Fields.export).get(ExportProperties.Fields.tileMaxzoom).orElse(maxZoom).asInt()
				);
				
				//add properties but with exceptions
				for(var e:f.tags().entrySet()) {
					switch(e.getKey()) {
						case Properties.Fields.label->{
							if(f.getSource().equals("locations")) {
								out.setAttrWithMinzoom(e.getKey(), e.getValue(), Math.min(maxZoom, (int)f.getLong(Properties.Fields.pregroupMinzoom)+5));
							}
							else
								out.setAttr(e.getKey(), e.getValue());
						}
						case Properties.Fields.export->{}
						default->out.setAttr(e.getKey(), e.getValue());
					}
				}
				
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
    
    //hopefully this is just a temporary workaround until planetiler fixes https://github.com/onthegomap/planetiler/issues/1588
    private FeatureCollection shift(FeatureCollection fc) {
    	for(var f:fc.getFeatures()) {
    		if(f.getGeometry().streamPoints().allMatch(p->p.lng()>180)) {
    			f.setGeometry(translate(f.getGeometry(), ln->ln-360));
    		}
    		if(f.getGeometry().streamPoints().allMatch(p->p.lng()<-180)) {
    			f.setGeometry(translate(f.getGeometry(), ln->ln+360));
    		}
    	}
		return fc;
	}

	private <T> Geometry translate(Geometry geometry, DoubleUnaryOperator lngChanger) {
		geometry.transformPoints(p->
			new LngLat(lngChanger.applyAsDouble(p.lng()), p.lat())
		);
		return geometry;
	}

	@Override
    public List<StepExecutor> createAutoSteps() {
    	var meta = new PropsMeta();
    	meta.setFilenameSuffix(filename);
    	meta.setDescription(new StepDescription(
    		getDescription().getId()+"_meta",
    		getDescription().getGroup(),
    		getDescription().getStep()+"_meta",
    		meta
    	));
    	meta.setId(meta.getDescription().getId());
    	meta.getInputMapping().putAll(this.getInputMapping());
    	meta.getInputMapping().put("highlights", "highlights.PREPARE_EXPORT");
    	return List.of(meta);
    }
}
