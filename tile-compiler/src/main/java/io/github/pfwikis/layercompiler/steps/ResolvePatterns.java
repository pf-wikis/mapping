package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties.Pattern;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class ResolvePatterns extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var byPattern = in.getInput().toFeatureCollection()
    		.getFeatures()
    		.stream()
    		.collect(Collectors.groupingBy(
    			f->Optional.ofNullable(f.getProperties().getPattern()).orElse(Pattern.NONE)
    		));
    	
    	var res = new FeatureCollection();
    	for(var e:byPattern.entrySet()) {
    		var result = switch(e.getKey()) {
    			case pebbles -> makePebbles(e.getValue());
    			case NONE -> e.getValue();
    			default -> throw new IllegalStateException();
    		};
    		res.getFeatures().addAll(result);
    	}
    	
        return Content.timeless(GeoData.from(res));
    }

	private List<Feature> makePebbles(List<Feature> features) throws IOException {
		var in = new FeatureCollection();
		in.setFeatures(features);
		var inf = GeoData.from(in);
		
		var dots = Tools.mapshaper(this, inf,
			"-each", "this.properties.number=this.area/250",
			"-dots", "number", "copy-fields=color"/*,
			"-symbols", "type=polygon", "geographic", "radius=.01", "sides=8"*/
		);
		
		var pebbles = Tools.qgis(this, "native:rectanglesovalsdiamonds", dots,
			"--SHAPE=2", //ovals
			"--WIDTH=0.0001",
			"--HEIGHT=expression:randf(0.0001,0.0002)",
			"--ROTATION=expression:rand(1,360)",
			"--SEGMENTS=12"
		);
		var out = Tools.mapshaper(this, pebbles,
            "-clip", inf
        );
		
		return out.toFeatureCollection().getFeatures();
	}

}
