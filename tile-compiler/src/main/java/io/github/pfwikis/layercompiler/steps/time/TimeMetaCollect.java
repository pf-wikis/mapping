package io.github.pfwikis.layercompiler.steps.time;

import java.util.List;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.time.TimeRange;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class TimeMetaCollect extends StepExecutor {
	
    @Override
    public Content process(Inputs in) throws Exception {
    	var barriers = new TreeSet<Integer>();
    	for(var layer : in.getInputs().values()) {
    		barriers.addAll(TimeSlicer.extractBarriers(layer.toFeatureCollection()));
    	}
    	
    	var slices = TimeSlicer.barriersToSlices(barriers)
    			.stream()
    			.map(ts->TimeMetaEntry.from(ts))
    			.toList();
    	for(int i=0;i<slices.size();i++) {
    		slices.get(i).id = 1+i-slices.size();
    	}
    	
    	if(slices.size()<=1) {
    		throw new IllegalStateException("The time metadata should have more than one entry");
    	}
    	
    	var fc = new FeatureCollection();
    	fc.getProperties().setTimeMeta(new TimeMeta(slices));
    	return Content.timeless(GeoData.from(fc));
    }
    
    @Getter @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class TimeMeta {
    	private List<TimeMetaEntry> entries;

    	public int getIndexForStart(TimeRange time) {
			for(var entry:entries) {
				if(time.intersects(entry.time))
					return entry.id;
			}
			throw new IllegalStateException();
		}
    	
    	public int getIndexForEnd(TimeRange time) {
    		for(var entry:entries.reversed()) {
				if(time.intersects(entry.time))
					return entry.id+1;
			}
			throw new IllegalStateException();
    	}

		public TimeRange indexToYears(Integer index) {
			if(index == null) return TimeRange.always();
			
			for(var entry:entries) {
				if(entry.id == index)
					return entry.time;
			}
			return entries.getLast().time;
		}
    	
    }
    
    @Getter @Setter
    @EqualsAndHashCode
    public static class TimeMetaEntry {
    	@JsonUnwrapped
    	private TimeRange time;
    	private int id;
    	
		public static TimeMetaEntry from(TimeRange time) {
			var res = new TimeMetaEntry();
			res.time = time;
			return res;
		}
    }
}
