package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.ColorUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MergeLabels extends LCStep {

    @Override
    public LCContent process() throws Exception {
    	var result = new FeatureCollection();
    	result.setFeatures(new ArrayList<>());
    	log.info("Label layer order: {}", getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining("->")));
    	for(var e:this.getInputs().entrySet()) {
    		var defaultColor = MergeGeometry.colorFor(e.getKey());

    		for(var f : e.getValue().toFeatureCollection().getFeatures()) {
    			result.getFeatures().add(f);
    			
    			Color color = Optional.ofNullable(f.getProperties().getColor())
    				.map(ColorUtil::fromHex)
    				.or(()->Optional.ofNullable(defaultColor))
    				.orElseThrow(()->new IllegalStateException("No color for feature in "+e.getKey()+":"+f.getProperties()));
    			
    			f.getProperties().setColor(ColorUtil.toHex(ColorUtil.darkenTo(color, .15)));
				f.getProperties().setHalo(ColorUtil.toHex(ColorUtil.brightenBy(color, .1)));
    		}
    	}
    	return LCContent.from(result);
    }
}

