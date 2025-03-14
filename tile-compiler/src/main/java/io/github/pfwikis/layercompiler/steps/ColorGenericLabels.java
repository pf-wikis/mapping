package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.util.ColorUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColorGenericLabels extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
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
    	return LCContent.from(fc);
    }
}
