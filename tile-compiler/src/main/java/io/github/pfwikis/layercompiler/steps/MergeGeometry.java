package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.ColorUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MergeGeometry extends LCStep {

    @Override
    public LCContent process() throws Exception {
    	var result = new FeatureCollection();
    	result.setFeatures(new ArrayList<>());
    	log.info("Geometry layer order: {}", getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining("->")));
    	for(var e:this.getInputs().entrySet()) {
    		var defaultColor = ColorUtil.toHex(colorFor(e.getKey()));
    		var features = Tools.mapshaper(this, e.getValue(),
				"-if", "this.field_exists('color')",
					"-dissolve2", "color",
				"-else",
					"-dissolve2",
				"-endif",
				"-explode"
			);
    		for(var f : features.toFeatureCollection().getFeatures()) {
    			result.getFeatures().add(f);
    			if(f.getProperties().getColor() == null)
    				f.getProperties().setColor(defaultColor);
    		}
    		features.finishUsage();
    	}
    	//we could think about doing a mosaic here
    	//return Tools.mapshaper(LCContent.from(result), "-mosaic", "calc='color=last(color)'");
    	return LCContent.from(result);
    }

	public static Color colorFor(String layer) {
		return switch(layer) {
			case "land" -> new Color(248, 241, 225);
			case "ice" -> new Color(1f,1f,1f,0.6f); 
			case "districts" -> new Color(212, 204, 185);
			case "deserts" -> new Color(255, 247, 190);
			case "swamp" -> new Color(183, 197, 188);
			case "mountains" -> new Color(222, 212, 184);
			case "hills" -> new Color(235, 227, 205);
			case "forests" -> new Color(187, 226, 198);
			case "swamps" -> new Color(183, 197, 188);
			case "shallow-waters", "waters" -> new Color(138, 180, 248);
			case "buildings" -> new Color(119, 136, 153);
			case "specials" -> new Color(255, 0, 0);
			case "generic" -> new Color(255, 0, 0);
			default -> throw new IllegalStateException("Unknown color for "+layer);
		};
	}

}
