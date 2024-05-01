package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddZoom extends LCStep {

    private final Integer minZoom;
    private final Integer maxZoom;

    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		if(minZoom != null) {
    			f.getProperties().setFilterMinzoom(minZoom);
            }
    		if(maxZoom != null) {
    			f.getProperties().setFilterMaxzoom(maxZoom);
            }
    	});
        return LCContent.from(fc);
    }
}
