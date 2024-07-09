package io.github.pfwikis.layercompiler.steps;

import java.util.ArrayList;
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
    			var color = f.getProperties().getColor()==null?defaultColor:ColorUtil.fromHex(f.getProperties().getColor());
    			if(f.getProperties().getColor() == null) {
    				f.getProperties().setColor(ColorUtil.toHex(ColorUtil.darken(color, .4f)));
    				f.getProperties().setHalo(ColorUtil.toHex(ColorUtil.darken(color, -.3f)));
    			}
    			else {
    				
    			}
    		}
    	}
    	return LCContent.from(result);
    }
}
