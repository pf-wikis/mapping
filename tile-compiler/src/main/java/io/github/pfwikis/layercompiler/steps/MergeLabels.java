package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.ColorUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class MergeLabels extends StepExecutor {

	private boolean invert = false;
	
    @Override
    public Content process(Inputs in) throws Exception {
    	var result = new FeatureCollection();
    	result.setFeatures(new ArrayList<>());
    	log.info("Label layer order: {}", in.getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining("->")));
    	for(var e:in.getInputs().entrySet()) {
    		var defaultColor = MergeGeometry.colorFor(e.getKey());

    		for(var f : e.getValue().toFeatureCollection().getFeatures()) {
    			result.getFeatures().add(f);
    			
    			Color color = Optional.ofNullable(f.getProperties().getColor())
    				.map(ColorUtil::fromHex)
    				.or(()->Optional.ofNullable(defaultColor))
    				.orElseThrow(()->new IllegalStateException("No color for feature in "+e.getKey()+":"+f.getProperties()));
    			
    			f.getProperties().setColor(ColorUtil.toHex(ColorUtil.darkenTo(color, .15)));
				f.getProperties().setHalo(ColorUtil.toHex(ColorUtil.brightenBy(color, .1)));
				
				if(invert) {
					var oldHalo = f.getProperties().getHalo();
					f.getProperties().setHalo(f.getProperties().getColor());
					f.getProperties().setColor(oldHalo);
				}
    		}
    	}
    	return Content.derivedFrom(in, GeoData.from(result));
    }
}

