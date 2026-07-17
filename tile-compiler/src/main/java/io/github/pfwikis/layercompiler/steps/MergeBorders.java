package io.github.pfwikis.layercompiler.steps;

import java.util.HashMap;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.ILineString;
import io.github.pfwikis.model.Geometry.LineString;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.TimeMap;
import io.github.pfwikis.util.TimeSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class MergeBorders extends StepExecutor {

    @Override
    public Content process(Inputs in) throws Exception {
    	var borderSegments = new HashMap<Segment, TimeMap<Integer>>();
    	var regionSegments = new HashMap<Segment, TimeSet>();
    	//split lines into component segments
    	for(var input:in.getInputs().entrySet()) {
    		if(input.getValue().isEmpty()) continue;
    		var fc = input.getValue().toFeatureCollection();
    		int type = switch(input.getKey()) {
				case "region" -> 1;
				case "subregion" -> 2;
				case "nation" -> 3;
				case "province" -> 4;
				case "district" -> 5;
				default -> throw new IllegalStateException();
    		};
    		for(var f:fc.getFeatures()) {
    			if(f.getGeometry() instanceof ILineString mline) {
    				for(var line:mline.toLines()) {
	    				for(int i=0;i<line.size()-1;i++) {
	    					var a=line.get(i).roundTo(7);
	    					var b=line.get(i+1).roundTo(7);
	    					if(a.lng()<b.lng() || (a.lng()==b.lng()&&a.lat()<b.lat())) {
	    						var c = a;
	    						a=b;
	    						b=c;
	    					}
	    					var seg = new Segment(a,b);
	    					if(type > 1)
		    					borderSegments.computeIfAbsent(seg, _->TimeMap.create())
		    						.merge(f.getProperties().getTime(), type, Math::min);
	    					else
	    						regionSegments.computeIfAbsent(seg, _->TimeSet.create())
	    							.add(f.getProperties().getTime());
	    				}
    				}
    			}
    		}
    	}
    	
    	var fc = new FeatureCollection();
    	for(var segEntry : borderSegments.entrySet()) {
    		var seg = segEntry.getKey();
    		
    		for(var entry:segEntry.getValue().entries()) {
    			var type = entry.getValue();
	    		var f = new Feature();
	    		fc.getFeatures().add(f);
	    		f.setGeometry(LineString.from(List.of(seg.a, seg.b)));
	    		f.getProperties().setBorderType(type);
	    		f.getProperties().setTime(entry.getKey());
    		}
    			
    		
    	}
    	for(var segEntry : regionSegments.entrySet()) {
    		var seg = segEntry.getKey();
    		for(var time:segEntry.getValue().asRanges()) {
	    		var f = new Feature();
	    		fc.getFeatures().add(f);
	    		f.setGeometry(LineString.from(List.of(seg.a, seg.b)));
	    		f.getProperties().setBorderType(1);
	    		f.getProperties().setMaxzoom(5);
	    		f.getProperties().setTime(time);
    		}
    	}
    	var lc = GeoData.from(fc);
    	
    	var res= Tools.mapshaper(this,
    		lc,
			"-dissolve", "borderType,timeStart,timeEnd,maxzoom",
			"-explode"
    	);
    	return Content.merged(res);
    	//merge borders
    	//merge overlapping lines while remembering their types
    	//merge min and max zoom
    	//probably an int type is fine
    }
	private record Segment(LngLat a, LngLat b) {}

}
