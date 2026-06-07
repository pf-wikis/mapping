package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.util.ColorUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class ColorGenericLabels extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		var t = f.getProperties().getType();
    		f.getProperties().setColor(switch(t) {
				case "waters", "land" -> ColorUtil.toHex(MergeGeometry.colorFor(t));
				case null, default -> {
					throw new IllegalStateException(
						"Can't determine color for generic label "
						+ f.getProperties().simpleLabel()
						+ " of type "
						+ t
					);
				}
			});
    	});
    	return Content.derivedFrom(in, GeoData.from(fc));
    }
}
