package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStepSlicing;
import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent;
import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TimeSlices extends LCStepSlicing {

    @Override
    public TimeSlicedContent process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	var slices = findSliceRanges(fc);
    	
    	for(var slice:slices) {
    		var rfc = new FeatureCollection();
    		slice.setContent(LCContent.from(rfc));
    		rfc.getProperties().setTimeStart(slice.getTime().hasLowerBound()?slice.getTime().lowerEndpoint():null);
    		rfc.getProperties().setTimeEnd(slice.getTime().hasUpperBound()?slice.getTime().upperEndpoint():null);
    		
    		for(var f:fc.getFeatures()) {
    			if(slice.shouldContain(f)) {
    				Feature c = f.copy();
    				c.getProperties().setTimeStart(null);
    				c.getProperties().setTimeEnd(null);
    				rfc.getFeatures().add(c);
    			}
    		}
    	}
    	
        return new TimeSlicedContent(slices);
    }
    
	private List<TimeSlice> findSliceRanges(FeatureCollection fc) {
		var barriers = new TreeSet<Integer>();
    	fc.getFeatures().forEach(f-> {
    		if(f.getProperties().getTimeStart() != null) {
    			barriers.add(f.getProperties().getTimeStart());
            }
    		if(f.getProperties().getTimeEnd() != null) {
    			barriers.add(f.getProperties().getTimeEnd());
            }
    	});
    	var arr = barriers.toArray(Integer[]::new);
    	var result = new ArrayList<TimeSlice>();

    	if(arr.length==0) return List.of(TimeSlice.fromRange(null, null));
    	
    	result.add(TimeSlice.fromRange(null, arr[0]));
    	for(int i=0;i<arr.length-1;i++) {
    		result.add(TimeSlice.fromRange(arr[i], arr[i+1]));
    	}
    	result.add(TimeSlice.fromRange(arr[arr.length-1], null));
    	return result;
	}
}
