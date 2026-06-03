package io.github.pfwikis.layercompiler.steps.model;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import org.apache.commons.lang3.tuple.Pair;

import com.github.dexecutor.core.task.Task;
import com.google.common.base.Stopwatch;
import com.google.common.collect.RangeMap;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.CLIOptions;
import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.time.TimeRange;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
public abstract class LCStepAbstract extends Task<String, TimeSlicedContent> {

    protected Ctx ctx;
    private String name;
    private String step;
    private int numberOfDependents;
    private SequencedMap<String, String> inputMapping = new LinkedHashMap<>();
    private Map<String, Duration> subTimings = new HashMap<>();


    protected abstract TimeSlicedContent executeInternal() throws Exception;
    
    
    public static void setThreadName(String name, String step, String variant) {
    	if(variant != null)
    		Thread.currentThread().setName(name+"."+step+"."+variant);
    	else
    		Thread.currentThread().setName(name+"."+step);
    }
    @Override
    public TimeSlicedContent execute() {
    	var oldName = Thread.currentThread().getName();
        try {
        	setThreadName(name, step, null);
        	var results = executeInternal();
            return results;
        } catch (Throwable t) {
        	log.error("Failed execution", t);
            throw new RuntimeException("Failed execution with: "+t.getMessage());
        } finally {
        	Thread.currentThread().setName(oldName);
        }
    }
    
	protected List<Inputs> createVariants() {
		if(inputMapping.isEmpty())
			return List.of(new Inputs(TimeRange.always()));
		
		var allInputs = inputMapping.entrySet().stream().map(e->Pair.of(e.getKey(), getResult(e.getValue()).getResult())).toList();
		var allCoveredTime = TreeRangeSet.<Integer>create();
		allInputs.forEach((e)->e.getValue().getSlices().forEach(s->allCoveredTime.add(s.getTime().toGuavaRange())));
		
		return new ArrayList<>(allInputs.stream()
			.map(in->createVariants(allCoveredTime, in.getKey(), in.getValue()))
			.reduce(this::mergeVariants).get()
			.asMapOfRanges()
			.values());
	}
    
    private RangeMap<Integer, Inputs> mergeVariants(RangeMap<Integer, Inputs> a, RangeMap<Integer, Inputs> b) {
    	var result = TreeRangeMap.<Integer, Inputs>create();
    	for(var right:b.asMapOfRanges().entrySet()) {
    		for(var leftMatch:a.subRangeMap(right.getKey()).asMapOfRanges().entrySet()) {
    			var variant = new Inputs(TimeRange.from(leftMatch.getKey()));
    			variant.inputs.putAll(leftMatch.getValue().inputs);
    			variant.inputs.putAll(right.getValue().inputs);
    			result.put(leftMatch.getKey(), variant);
    		}
    	}
    	return result;
    }
    
    private RangeMap<Integer, Inputs> createVariants(RangeSet<Integer> allCoveredTime, String key, TimeSlicedContent slices) {
    	var result=TreeRangeMap.<Integer, Inputs>create();
    	for(var slice:slices.getSlices()) {
    		if(!result.subRangeMap(slice.getTime().toGuavaRange()).asMapOfRanges().isEmpty())
    			throw new IllegalStateException("This would create and error. We do not support overlapping ranges here.");
    		result.put(slice.getTime().toGuavaRange(), Inputs.from(key, slice));
    	}
    	
    	var missing = TreeRangeSet.create(allCoveredTime);
    	missing.removeAll(result.asMapOfRanges().keySet());
    	for(var time:missing.asRanges()) {
    		result.put(time, Inputs.from(key, TimeRange.from(time), LCContent.from(new FeatureCollection())));
    	}
    	return result;
    }
    
    public record Timing(String key, Stopwatch watch, LCStepAbstract step) implements Closeable {
		@Override
		public void close() {
			var time = watch.stop().elapsed();
			synchronized(step.subTimings) {
				step.subTimings.merge(key, time, Duration::plus);
			}
		}
    }
    protected Timing measureSubtime(String key) {
    	return new Timing(key, Stopwatch.createStarted(), this);
    }
    
    @Getter
    @RequiredArgsConstructor
    public static class Inputs {
    	private final TimeRange time;
    	private final SequencedMap<String, LCContent> inputs = new LinkedHashMap<>();
    	
    	public static Inputs from(String key, TimeSlice slice) {
			var res = new Inputs(slice.getTime());
			res.inputs.put(key, slice.getContent());
			return res;
		}
    	
    	public static Inputs from(String key, TimeRange time, LCContent content) {
			var res = new Inputs(time);
			res.inputs.put(key, content);
			return res;
		}
    	
    	public static Inputs from(TimeRange time, Map<String, LCContent> inputs) {
    		var res = new Inputs(time);
			res.inputs.putAll(inputs);
			return res;
		}
    	
    	public LCContent getInput() {
            return getInput("in");
        }
    	
		public LCContent getInput(String key) {
            return inputs.get(key);
        }
    	
    	@Override
    	public String toString() {
    		return time.toString();
    	}
    }

	@Value
    public static class Ctx {
        private final CLIOptions options;
        private final File geo;
        private final File mappingDataFile;
    }

    public void init(Ctx ctx) {
        this.ctx = ctx;
    }
}
