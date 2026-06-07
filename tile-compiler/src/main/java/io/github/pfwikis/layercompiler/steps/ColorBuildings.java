package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.util.ColorUtil;

@Time.Requirement(Time.Requirement.Value.ANY)
public class ColorBuildings extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		f.getProperties().setColor(ColorUtil.toHex(switch(f.getProperties().getType()) {
				case "fortification" -> new Color(105, 105, 105);
				case "bridge" -> new Color(169, 169, 169);
				case null, default -> new Color(119, 136, 153);
    		}));
    	});
    	return Content.derivedFrom(in, GeoData.from(fc));
    }
}
