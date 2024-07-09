package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.util.ColorUtil;

public class ColorLabels extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		var t = f.getProperties().getType();
    		f.getProperties().setColor(ColorUtil.toHex(switch(t) {
				case "waters", "land" -> MergeGeometry.colorFor(t);
				case null, default -> new Color(255, 0, 0);
				//throw new IllegalStateException("Don't know how to color "+t);
			}));
    	});
    	return LCContent.from(fc);
    }
}
