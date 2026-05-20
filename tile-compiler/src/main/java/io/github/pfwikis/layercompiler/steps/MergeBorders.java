package io.github.pfwikis.layercompiler.steps;

import java.util.HashMap;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.ILineString;
import io.github.pfwikis.model.Geometry.LineString;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MergeBorders extends LCStep {

    @Override
    public LCContent process(Inputs in) throws Exception {
    	var segments = new HashMap<Segment, Integer>();  	
    	//split lines into component segments
    	for(var input:in.getInputs().entrySet()) {
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
	    					segments.merge(new Segment(a,b), type, Integer::min);
	    				}
    				}
    			}
    		}
    	}
    	
    	var fc = new FeatureCollection();
    	for(var seg : segments.entrySet()) {
    		var f = new Feature();
    		fc.getFeatures().add(f);
    		f.setGeometry(new LineString(List.of(seg.getKey().a, seg.getKey().b)));
    		f.getProperties().setBorderType(seg.getValue());
    	}
    	var lc = LCContent.from(fc);
    	
    	var res= Tools.mapshaper(this,
    		lc,
			"-dissolve", "borderType"
    	);
    	return res;
    	//merge borders
    	//merge overlapping lines while remembering their types
    	//merge min and max zoom
    	//probably an int type is fine
    }

	private record Segment(LngLat a, LngLat b) {}

}
