package io.github.pfwikis.layercompiler.steps;

import java.util.List;
import java.util.TreeSet;

import com.google.common.collect.Range;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStepMergingTime;
import io.github.pfwikis.model.FeatureCollection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
public class TimeMetaCollect extends LCStepMergingTime {
	
    @Override
    public LCContent process(Inputs in) throws Exception {
    	var barriers = new TreeSet<Integer>();
    	for(var layer : in.getInputs().values()) {
    		barriers.addAll(TimeSlices.extractBarriers(layer.toFeatureCollection()));
    	}
    	
    	var slices = TimeSlices.barriersToSlices(barriers)
    			.stream()
    			.map(ts->TimeMetaEntry.from(ts.getTime()))
    			.toList();
    	for(int i=0;i<slices.size();i++) {
    		slices.get(i).id = 1+i-slices.size();
    	}
    	
    	var fc = new FeatureCollection();
    	fc.getProperties().setTimeMeta(new TimeMeta(slices));
    	return LCContent.from(fc);
    }
    
    @Getter @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class TimeMeta {
    	private List<TimeMetaEntry> entries;

    	public int getIndexForStart(Range<Integer> time) {
			for(var entry:entries) {
				if(time.isConnected(entry.time) && !time.intersection(entry.time).isEmpty())
					return entry.id;
			}
			throw new IllegalStateException();
		}
    	
    	public int getIndexForEnd(Range<Integer> time) {
    		for(var entry:entries.reversed()) {
				if(time.isConnected(entry.time) && !time.intersection(entry.time).isEmpty())
					return entry.id;
			}
			throw new IllegalStateException();
    	}
    	
    }
    
    @Getter @Setter
    @EqualsAndHashCode
    public static class TimeMetaEntry {
    	private Range<Integer> time;
    	private int id;
    	
		public static TimeMetaEntry from(Range<Integer> time) {
			var res = new TimeMetaEntry();
			res.time = time;
			return res;
		}
    }
}
