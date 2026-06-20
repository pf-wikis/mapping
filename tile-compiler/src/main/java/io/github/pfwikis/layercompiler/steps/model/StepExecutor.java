package io.github.pfwikis.layercompiler.steps.model;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.github.dexecutor.core.task.Task;
import com.google.common.base.Stopwatch;

import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.Time.ContentState;
import io.github.pfwikis.layercompiler.steps.model.Time.DataState;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.TimeMap;
import io.github.pfwikis.util.TimeSet;
import io.github.pfwikis.util.time.TimeRange;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
public abstract class StepExecutor extends Task<String, Content> {

    protected StepDescription description;
    private SequencedMap<String, String> inputMapping = new LinkedHashMap<>();
    private Map<String, Duration> subTimings = new HashMap<>();


    protected abstract Content process(Inputs in) throws Exception;


    public static void setThreadName(String group, String step, String variant) {
    	if(variant != null)
    		Thread.currentThread().setName(group+"."+step+"."+variant);
    	else
    		Thread.currentThread().setName(group+"."+step);
    }

    @Override
    public Content execute() {
    	var oldName = Thread.currentThread().getName();
        try {
        	setThreadName(description.getGroup(), description.getStep(), null);
        	
        	//we need to differentiate between different cases based on our requirement
        	Content results = switch(description.getTimeRequirement()) {
        		case ANY,REQUIRES_MERGED -> executeWithMergedContents();
        		case REQUIRES_SLICED -> executeWithSlicedInputs();
        	};
        	
        	return results;
        } catch (Throwable t) {
        	log.error("Failed execution", t);
            throw new RuntimeException("Failed execution with: "+t.getMessage(), t);
        } finally {
        	Thread.currentThread().setName(oldName);
        }
    }

    private Content executeWithSlicedInputs() throws Exception {
		var variants = createSlicedVariants();
		
		//shortcut in case there is only one
		if(variants.size()==1) {
			var variant = variants.getFirst();
			var res = limitContentToTime(process(variant), variant.getTime()).asTimelessIfPossible();
			return res;
		}
		
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = variants
				.stream()
				.map(v-> executor.submit(() -> {
					StepExecutor.setThreadName(description.getGroup(), description.getStep(), v.getTime().toString());
					return limitContentToTime(process(v),v.getTime());
				}))
				.toList();
			var results = new ArrayList<TimeSlice>(futures.size());
		    try {
		        for (var f : futures) {
		            results.addAll(f.get().getSlices());
		        }
		        return Content.sliced(results);
		    } catch (Exception e) {
		    	futures.forEach(f->f.cancel(true));
		    	throw e;
		    }
		}
	}
    
    private TimeSlicedContent limitContentToTime(Content content, TimeRange range) {
    	if(range.equals(TimeRange.always())) {
    		return content.asSliced();
    	}
		return Content.sliced(content.asSliced()
			.getSlices()
			.stream()
			.map(sl->new TimeSlice(sl.getTime().intersection(range), sl.getData()))
			.toList());
    }

	private Content executeWithMergedContents() throws Exception {
		Map<String, GeoData> inputs = new LinkedHashMap<>();
		var state = DataState.TIMELESS;
		for(var e:getAllInputs(Content::asMergedOrTimeless)) {
			if(e.getValue().getTimeState() == ContentState.MERGED)
				state = DataState.MERGED;
			inputs.put(e.getKey(), e.getValue().getData());
		}
		var in = Inputs.from(state, TimeRange.always(), inputs);
		return process(in);
	}

	protected List<Inputs> createSlicedVariants() {
		//special case for no inputs
		if(inputMapping.isEmpty())
			return List.of(Inputs.from(DataState.TIMELESS, TimeRange.always(), Collections.emptyMap()));
		
		var allInputs = getAllInputs(Content::asSliced);
		var allCoveredTime = TimeSet.create();
		allInputs.forEach((e)->e.getValue().getSlices().forEach(s->allCoveredTime.add(s.getTime())));
		
		return allInputs.stream()
			.map(in->createSlicedVariants(allCoveredTime, in.getKey(), in.getValue()))
			.reduce(this::mergeVariants).get()
			.values();
	}
	
	private TimeMap<Inputs> mergeVariants(TimeMap<Inputs> a, TimeMap<Inputs> b) {
		var result = TimeMap.<Inputs>create();
		for(var right:b.entries()) {
			for(var leftMatch:a.subTimeMap(right.getKey()).entries()) {
				var variant = Inputs.from(DataState.TIMELESS, leftMatch.getKey());
				variant.getInputs().putAll(leftMatch.getValue().getInputs());
				variant.getInputs().putAll(right.getValue().getInputs());
				result.put(leftMatch.getKey(), variant);
			}
		}
		return result;
	}
	
	private TimeMap<Inputs> createSlicedVariants(TimeSet allCoveredTime, String key, TimeSlicedContent slices) {
		var result=TimeMap.<Inputs>create();
		for(var slice:slices.getSlices()) {
			if(!result.subTimeMap(slice.getTime()).isEmpty())
				throw new IllegalStateException("This would create and error. We do not support overlapping ranges here.");
			result.put(slice.getTime(), Inputs.from(DataState.TIMELESS, key, slice));
		}
		
		var missing = TimeSet.create(allCoveredTime);
		missing.removeAll(result.ranges());
		for(var time:missing.asRanges()) {
			result.put(time, Inputs.from(DataState.TIMELESS, key, time, GeoData.from(new FeatureCollection())));
		}
		return result;
	}

	private <T extends Content> List<Pair<String, T>> getAllInputs(Function<Content, T> transform) {
		var res = new ArrayList<Pair<String, T>>(inputMapping.size());
		for(var e:inputMapping.entrySet()) {
			var key = e.getKey();
			var content = getResult(e.getValue());
			res.add(Pair.of(key, transform.apply(content.getResult())));
		}
		return res;
	}
	
	public record Timing(String key, Stopwatch watch, StepExecutor step) implements Closeable {
		@Override
		public void close() {
			var time = watch.stop().elapsed();
			synchronized(step.subTimings) {
				step.subTimings.merge(key, time, Duration::plus);
			}
		}
    }
    public Timing measureSubtime(String key) {
    	return new Timing(key, Stopwatch.createStarted(), this);
    }


	public List<StepExecutor> createAutoSteps() {return Collections.emptyList();}
}
