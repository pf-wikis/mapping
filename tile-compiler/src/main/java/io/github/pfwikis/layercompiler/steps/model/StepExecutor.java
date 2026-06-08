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
import com.google.common.collect.RangeMap;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.Time.DataState;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
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
		for(var e:getAllInputs(Content::asMerged)) {
			inputs.put(e.getKey(), e.getValue().getData());
		}
		var in = Inputs.from(DataState.MERGED, TimeRange.always(), inputs);
		return process(in);
	}

	protected List<Inputs> createSlicedVariants() {
		//special case for no inputs
		if(inputMapping.isEmpty())
			return List.of(Inputs.from(DataState.TIMELESS, TimeRange.always(), Collections.emptyMap()));
		
		var allInputs = getAllInputs(Content::asSliced);
		var allCoveredTime = TreeRangeSet.<Integer>create();
		allInputs.forEach((e)->e.getValue().getSlices().forEach(s->allCoveredTime.add(s.getTime().toGuavaRange())));
		
		return new ArrayList<>(allInputs.stream()
			.map(in->createSlicedVariants(allCoveredTime, in.getKey(), in.getValue()))
			.reduce(this::mergeVariants).get()
			.asMapOfRanges()
			.values());
	}
	
	private RangeMap<Integer, Inputs> mergeVariants(RangeMap<Integer, Inputs> a, RangeMap<Integer, Inputs> b) {
		var result = TreeRangeMap.<Integer, Inputs>create();
		for(var right:b.asMapOfRanges().entrySet()) {
			for(var leftMatch:a.subRangeMap(right.getKey()).asMapOfRanges().entrySet()) {
				var variant = Inputs.from(DataState.TIMELESS, TimeRange.from(leftMatch.getKey()));
				variant.getInputs().putAll(leftMatch.getValue().getInputs());
				variant.getInputs().putAll(right.getValue().getInputs());
				result.put(leftMatch.getKey(), variant);
			}
		}
		return result;
	}
	
	private RangeMap<Integer, Inputs> createSlicedVariants(RangeSet<Integer> allCoveredTime, String key, TimeSlicedContent slices) {
		var result=TreeRangeMap.<Integer, Inputs>create();
		for(var slice:slices.getSlices()) {
			if(!result.subRangeMap(slice.getTime().toGuavaRange()).asMapOfRanges().isEmpty())
				throw new IllegalStateException("This would create and error. We do not support overlapping ranges here.");
			result.put(slice.getTime().toGuavaRange(), Inputs.from(DataState.TIMELESS, key, slice));
		}
		
		var missing = TreeRangeSet.create(allCoveredTime);
		missing.removeAll(result.asMapOfRanges().keySet());
		for(var time:missing.asRanges()) {
			result.put(time, Inputs.from(DataState.TIMELESS, key, TimeRange.from(time), GeoData.from(new FeatureCollection())));
		}
		return result;
	}

	private <T extends Content> List<Pair<String, T>> getAllInputs(Function<Content, T> transform) {
		return inputMapping.entrySet().stream()
				.map(e->Pair.of(e.getKey(), transform.apply(getResult(e.getValue()).getResult())))
				.toList();
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
    
    
}
