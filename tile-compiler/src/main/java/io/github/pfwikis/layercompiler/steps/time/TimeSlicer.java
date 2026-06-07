package io.github.pfwikis.layercompiler.steps.time;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.time.TimeRange;

public class TimeSlicer {
	
	public static TreeSet<Integer> extractBarriers(FeatureCollection fc) {
		var barriers = new TreeSet<Integer>();
		fc.getFeatures().forEach(f-> {
			f.getProperties().getTime().forEachBarrier(barriers::add);
		});
		return barriers;
	}

	public static List<TimeRange> barriersToSlices(TreeSet<Integer> barriers) {
		var arr = barriers.toArray(Integer[]::new);
		var result = new ArrayList<TimeRange>();

		if(arr.length==0) return List.of(TimeRange.always());
		
		result.add(new TimeRange(null, arr[0]));
		for(int i=0;i<arr.length-1;i++) {
			result.add(new TimeRange(arr[i], arr[i+1]));
		}
		result.add(new TimeRange(arr[arr.length-1], null));
		return result;
	}
}
