package io.github.pfwikis.layercompiler.steps.time;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import io.github.pfwikis.layercompiler.steps.model.LCStepSlicing;
import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent;
import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.time.TimeRange;
import lombok.Setter;

@Setter
public class TimeMetaLimit extends LCStepSlicing {
	
	private TimeRange time;

    @Override
    public TimeSlicedContent process(Inputs in) throws IOException {
    	if(!time.intersects(in.getTime()))
    		return new TimeSlicedContent(List.of());
    	var fc = in.getInput();
    	return new TimeSlicedContent(List.of(TimeSlice.from(
    		in.getTime().intersection(time),
    		fc
    	)));
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
