package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.util.ColorUtil;

public class ColorBuildings extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		f.getProperties().setColor(ColorUtil.toHex(switch(f.getProperties().getType()) {
				case "fortification" -> new Color(105, 105, 105);
				case "bridge" -> new Color(169, 169, 169);
				case null, default -> new Color(119, 136, 153);
    		}));
    	});
    	return LCContent.from(fc);
    }
}
