package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;

@Time.Requirement(Time.Requirement.Value.ANY)
public class CityTypeToIcon extends StepExecutor {
    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
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
    	return Content.derivedFrom(in,  GeoData.from(fc));
    }
}
