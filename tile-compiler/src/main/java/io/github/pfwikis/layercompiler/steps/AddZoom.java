package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import lombok.RequiredArgsConstructor;

@Time.Requirement(Time.Requirement.Value.ANY)
@RequiredArgsConstructor
public class AddZoom extends StepExecutor {

    private final Integer minZoom;
    private final Integer maxZoom;

    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		if(minZoom != null) {
    			f.getProperties().setMinzoom(minZoom);
            }
    		if(maxZoom != null) {
    			f.getProperties().setMaxzoom(maxZoom);
            }
    	});
        return Content.derivedFrom(in, GeoData.from(fc));
    }
}
