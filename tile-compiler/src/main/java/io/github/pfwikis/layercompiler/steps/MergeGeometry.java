package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.ColorUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MergeGeometry extends LCStep {

    @Override
    public LCContent process() throws Exception {
    	var agg = new FeatureCollection();
    	log.info("Geometry layer order: {}", getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining("->")));
    	for(var e:this.getInputs().entrySet()) {
    		var defaultColor = Optional.ofNullable(colorFor(e.getKey()))
				.map(ColorUtil::toHex);
    		
    		var defLambda = defaultColor
				.map(c->"if(!color) color='"+c+"'")
				.orElse("if(!color) throw new Error('element without required color')");
    		
    		var dissolved = Tools.mapshaper(this, e.getValue(),
    			"-each", defLambda,
				"-dissolve2", "color",
				"-explode"
			);
    		
    		var features = dissolved.toFeatureCollection();
    		dissolved.finishUsage();
    		for(var f : features.getFeatures()) {
    			agg.getFeatures().add(f);
    		}
    	}
    	var aggC = LCContent.from(agg);
    	//return LCContent.from(result);
    	var mosaicC = Tools.mapshaper(this, aggC,
			"-mosaic", "calc='colorStack=collect(color)'",
			"-filter", "Boolean(colorStack)",
			"-filter", "!this.isNull"
		);
    	aggC.finishUsage();
    	agg = null;
    	var mosaic = mosaicC.toFeatureCollectionAndFinish();
    	for(var f:mosaic.getFeatures()) {
    		Color c = new Color(110, 160, 245);
    		for(var rawNext:f.getProperties().getColorStack()) {
    			var next = ColorUtil.fromHex(rawNext);
    			if(next.getAlpha()==255) {
    				c=next;
    			}
    			else {
    				c = new Color(
						(next.getRed()  *next.getAlpha()+c.getRed()  *(255-next.getAlpha()))/255,
						(next.getGreen()*next.getAlpha()+c.getGreen()*(255-next.getAlpha()))/255,
						(next.getBlue() *next.getAlpha()+c.getBlue() *(255-next.getAlpha()))/255
					);
    			}
    		}
    		f.getProperties().setColorStack(null);
    		f.getProperties().setColor(ColorUtil.toHex(c));
    	}

    	var coloredC = LCContent.from(mosaic);
    	return Tools.mapshaper(this, coloredC,
			"-dissolve2", "color",
			"-explode"
		);
    }

	public static Color colorFor(String layer) {
		return switch(layer) {
			case "land" -> new Color(248, 241, 225);
			case "ice" -> new Color(1f,1f,1f,0.75f); 
			case "districts" -> new Color(212, 204, 185);
			case "deserts" -> new Color(255, 247, 190);
			case "swamp" -> new Color(183, 197, 188);
			case "mountains" -> new Color(222, 212, 184);
			case "hills" -> new Color(235, 227, 205);
			case "forests" -> new Color(187, 226, 198);
			case "swamps" -> new Color(183, 197, 188);
			case "rivers", "shallow-waters", "waters" -> new Color(138, 180, 248);
			case "buildings" -> new Color(119, 136, 153);
			case "roads" -> new Color(185, 157,  92);
			case "specials" -> null;//new Color(255, 0, 0);
			case "continents" -> new Color(248, 241, 225);
			case "generic" -> null;//new Color(255, 0, 0);
			default -> throw new IllegalStateException("Unknown color for "+layer);
		};
	}

}
