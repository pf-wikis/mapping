package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class LocationTypeToIcon extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var icons = Arrays.stream(new File("sprites").list())
    		.filter(n->n.startsWith("location-") && n.endsWith(".svg"))
    		.map(n->n.substring(9, n.length()-4))
    		.collect(Collectors.toSet());
    	
    	var fc = getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		if(icons.contains(f.getProperties().getType())) {
    			f.getProperties().setIcon("location-"+f.getProperties().getType());
    		}
    		else {
    			f.getProperties().setIcon("location-other");
    		}
    		f.getProperties().setType(null);
    	});
    	return LCContent.from(fc);
    }
}
