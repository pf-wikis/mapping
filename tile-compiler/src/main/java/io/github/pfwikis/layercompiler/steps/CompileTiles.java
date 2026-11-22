package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.tile.MltPmtilesCompiler;
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
    	var layers = new LinkedHashMap<String, FeatureCollection>();
    	in.getInputs().entrySet()
    		.stream()
    		.filter(l -> !l.getKey().equals("time-meta"))
    		.forEach(e -> layers.put(e.getKey(), shift(e.getValue().toFeatureCollection())));
    	new MltPmtilesCompiler().compile(
    		layers,
    		maxZoom,
    		new File(Ctx.INSTANCE.getOptions().targetDirectory(), filename+"."+extension).toPath()
    	);

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
