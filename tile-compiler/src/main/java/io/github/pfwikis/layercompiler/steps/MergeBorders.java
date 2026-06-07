package io.github.pfwikis.layercompiler.steps;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;

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
import io.github.pfwikis.util.time.TimeRange;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class MergeBorders extends StepExecutor {

    @Override
    public Content process(Inputs in) throws Exception {
    	var segments = new HashMap<Segment, TreeRangeMap<Integer, Integer>>();  	
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
	    					segments.computeIfAbsent(new Segment(a,b), _->TreeRangeMap.create())
	    						.merge(f.getProperties().getTime().toGuavaRange(), type, Math::min);
	    				}
    				}
    			}
    		}
    	}
    	
    	var fc = new FeatureCollection();
    	for(var segEntry : segments.entrySet()) {
    		var seg = segEntry.getKey();
    		var perValue = segEntry.getValue().asMapOfRanges()
    			.entrySet()
    			.stream()
    			.collect(Collectors.groupingBy(e->e.getValue()));
    		
    		for(var entry:perValue.entrySet()) {
    			int type = entry.getKey();
    			//this is done because the ranges might not be all merged here
    			var times = TreeRangeSet.create(entry.getValue().stream().map(Entry::getKey).toList());
    			
    			for(var time:times.asRanges()) {
    	    		var f = new Feature();
    	    		fc.getFeatures().add(f);
    	    		f.setGeometry(new LineString(List.of(seg.a, seg.b)));
    	    		f.getProperties().setBorderType(type);
    	    		f.getProperties().setTime(TimeRange.from(time));
        		}
    		}
    			
    		
    	}
    	var lc = GeoData.from(fc);
    	
    	var res= Tools.mapshaper(this,
    		lc,
			"-dissolve", "borderType,timeStart,timeEnd"
    	);
    	return Content.merged(res);
    	//merge borders
    	//merge overlapping lines while remembering their types
    	//merge min and max zoom
    	//probably an int type is fine
    }

	private record Segment(LngLat a, LngLat b) {}

}
