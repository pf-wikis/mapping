package io.github.pfwikis.layercompiler.steps;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.ColorUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class MergeGeometry extends StepExecutor {

    @Override
    public Content process(Inputs in) throws Exception {
    	var layeredOnEachOther = new FeatureCollection();
    	log.info("Geometry layer order: {}", in.getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining("->")));
    	for(var e:in.getInputs().entrySet()) {
    		var defaultColor = Optional.ofNullable(colorFor(e.getKey()))
				.map(ColorUtil::toHex);
    		
    		var defLambda = defaultColor
				.map(c->"if(!color) color='"+c+"'")
				.orElse("if(!color) throw new Error('element without required color')");
    		
    		var dissolved = Tools.mapshaper(this, e.getValue(),
    			"-each", defLambda,
				"-dissolve", "color",
				"-explode"
			);
    		
    		for(var f : dissolved.toFeatureCollection().getFeatures()) {
    			f.getProperties().setUuid(UUID.randomUUID());
    			layeredOnEachOther.getFeatures().add(f);
    		}
    	}
    	var mosaic = Tools.mapshaper(this, GeoData.from(layeredOnEachOther),
			"-mosaic", "calc='colorStack=collect(color)"
						+",uuids=collect(uuid)'",
			"-filter", "Boolean(colorStack)",
			"-filter", "!this.isNull"
		).toFeatureCollection();
    	Map<UUID, List<Feature>> resolved = new HashMap<>();
    	for(var f:mosaic.getFeatures()) {
    		Color c = new Color(110, 160, 245);
    		UUID resolvedTransparent = null;
    		var colorStack = f.getProperties().getColorStack();
    		var uuidStack = f.getProperties().getUuids();
    		if(f.getGeometry()==null)
				continue;
    		
    		for(int i=0;i<colorStack.size();i++) {
    			var uuid = uuidStack.get(i);
    			var next = ColorUtil.fromHex(colorStack.get(i));
    			if(next.getAlpha()==255) {
    				c=next;
    				resolvedTransparent = null;
    			}
    			else {
    				c = new Color(
						(next.getRed()  *next.getAlpha()+c.getRed()  *(255-next.getAlpha()))/255,
						(next.getGreen()*next.getAlpha()+c.getGreen()*(255-next.getAlpha()))/255,
						(next.getBlue() *next.getAlpha()+c.getBlue() *(255-next.getAlpha()))/255
					);
    				resolvedTransparent = uuid;
    			}
    		}
    		
    		if(resolvedTransparent != null) {
    			Feature mf = new Feature();
    			mf.getProperties().setColor(ColorUtil.toHex(c));
    			
    			mf.setGeometry(f.getGeometry());
    			resolved.computeIfAbsent(resolvedTransparent, _->new ArrayList<>())
    				.add(mf);
    		}
    	}
    	
    	FeatureCollection merged = new FeatureCollection();
    	for(var f:layeredOnEachOther.getFeatures()) {
    		var res = resolved.get(f.getProperties().getUuid());
    		if(res != null) {
    			merged.getFeatures().addAll(res);
    		}
    		else {
    			merged.getFeatures().add(f);
    			f.getProperties().setUuid(null);
    		}
    	}
    	
    	return Content.timeless(GeoData.from(merged));
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
