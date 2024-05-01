package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class CityTypeToIcon extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		f.getProperties().setIcon(switch(f.getProperties().getSize()) {
    			case 1 -> "city-large";
    			case 2 -> "city-medium";
    			case 3 -> "city-small";
    			case null,
    			default -> "city-major";
    		});
    		if(Boolean.TRUE.equals(f.getProperties().getCapital())) {
    			f.getProperties().setIcon(f.getProperties().getIcon()+"-capital");
    		}
    		f.getProperties().setCapital(null);
    		f.getProperties().setSize(null);
    	});
    	return LCContent.from(fc);
    }
}
