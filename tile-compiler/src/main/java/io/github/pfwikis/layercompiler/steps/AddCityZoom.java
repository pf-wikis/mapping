package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.Set;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;

@Time.Requirement(Time.Requirement.Value.ANY)
public class AddCityZoom extends StepExecutor {

	private static final Set<String> DISTRICT_CITIES = Set.of(
		"Absalom",
		"Shoreline",
		"Westerhold",
		"Copperwood",
		"Dawnfoot",
		"Magnimar"
	);
	
    @Override
    public Content process(Inputs ins) throws IOException {
    	var in = ins.getInput().toFeatureCollection();
    	in.getFeatures().forEach(f-> {
    		f.getProperties().setMinzoom(switch(f.getProperties().getSize()) {
    			case 0 -> 2;
    			case 1 -> 3;
    			case 2 -> 4;
    			case null,
    			default -> 4;
    		});
    		if(DISTRICT_CITIES.contains(f.getProperties().simpleLabel())) {
    			f.getProperties().setMaxzoom(11);
    		}
    	});
        return Content.derivedFrom(ins, GeoData.from(in));
    }

}
