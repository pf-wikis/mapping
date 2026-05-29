package io.github.pfwikis.layercompiler.steps.time;

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
import io.github.pfwikis.util.time.TimeRange;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TimeSlices extends LCStepSlicing {

    @Override
    public TimeSlicedContent process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	var barriers = extractBarriers(fc);
    	var slices = barriersToSlices(barriers);
    	
    	for(var slice:slices) {
    		var rfc = new FeatureCollection();
    		slice.setContent(LCContent.from(rfc));
    		rfc.getProperties().setTime(slice.getTime());
    		
    		for(var f:fc.getFeatures()) {
    			if(slice.shouldContain(f)) {
    				Feature c = f.copy();
    				c.getProperties().setTime(slice.getTime());
    				rfc.getFeatures().add(c);
    			}
    		}
    	}
    	
        return new TimeSlicedContent(slices);
    }

	public static TreeSet<Integer> extractBarriers(FeatureCollection fc) {
		var barriers = new TreeSet<Integer>();
    	fc.getFeatures().forEach(f-> {
    		f.getProperties().getTime().forEachBarrier(barriers::add);
    	});
    	return barriers;
	}

	public static List<TimeSlice> barriersToSlices(TreeSet<Integer> barriers) {
		var arr = barriers.toArray(Integer[]::new);
    	var result = new ArrayList<TimeSlice>();

    	if(arr.length==0) return List.of(TimeSlice.from(TimeRange.always()));
    	
    	result.add(TimeSlice.from(new TimeRange(null, arr[0])));
    	for(int i=0;i<arr.length-1;i++) {
    		result.add(TimeSlice.from(new TimeRange(arr[i], arr[i+1])));
    	}
    	result.add(TimeSlice.from(new TimeRange(arr[arr.length-1], null)));
    	return result;
	}
}
