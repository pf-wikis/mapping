package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class AddCityZoom extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	/*var in = getInput().toFeatureCollection();
    	in.getFeatures().forEach(f-> {
    		f.getProperties()
    	});
    	*/
        var result = Tools.mapshaper(getInput(), "-each", """
            filterMinzoom=(() => {switch(size) {
                case 0: return 1;
                case 1: return 2;
                case 2: return 3;
                default: return 3;
            }})();
            if(Name==='Absalom')
        		filterMaxzoom = 11;
        """);
        return result;
    }

}
