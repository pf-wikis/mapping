package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.Set;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class AddCityZoom extends LCStep {

	private static final Set<String> DISTRICT_CITIES = Set.of(
		"Absalom",
		"Shoreline",
		"Westerhold",
		"Copperwood",
		"Dawnfoot",
		"Magnimar"
	);
	
    @Override
    public LCContent process() throws IOException {
    	var in = getInput().toFeatureCollection();
    	in.getFeatures().forEach(f-> {
    		f.getProperties().setFilterMinzoom(switch(f.getProperties().getSize()) {
    			case 0 -> 1;
    			case 1 -> 2;
    			case 2 -> 3;
    			case null,
    			default -> 3;
    		});
    		if(DISTRICT_CITIES.contains(f.getProperties().simpleLabel())) {
    			f.getProperties().setFilterMaxzoom(11);
    		}
    	});
        return LCContent.from(in);
    }

}
