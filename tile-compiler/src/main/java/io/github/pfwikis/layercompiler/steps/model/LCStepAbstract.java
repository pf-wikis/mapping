package io.github.pfwikis.layercompiler.steps.model;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import com.github.dexecutor.core.task.Task;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;

import io.github.pfwikis.CLIOptions;
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
			return List.of(new Inputs(TimeRange.always(), new LinkedHashMap<>()));
		return inputMapping.entrySet().stream()
			.map(in->createVariants(in.getKey(), getResult(in.getValue()).getResult()))
			.reduce(this::mergeVariants).get();
	}
    
    private List<Inputs> mergeVariants(List<Inputs> a, List<Inputs> b) {
    	var result = new ArrayList<Inputs>();
    	var map=TreeRangeMap.<Integer, Inputs>create();
    	for(var i:a) {
    		if(!map.subRangeMap(i.time.toGuavaRange()).asMapOfRanges().isEmpty())
    			throw new IllegalStateException("This would create and error. We do not support overlapping ranges here.");
    		map.put(i.time.toGuavaRange(), i);
    	}
    	for(var right:b) {
    		for(var left:map.subRangeMap(right.time.toGuavaRange()).asMapOfRanges().entrySet()) {
    			var variant = new Inputs(TimeRange.from(left.getKey()), new LinkedHashMap<>(left.getValue().inputs));
    			variant.inputs.putAll(right.inputs);
    			result.add(variant);
    		}
    	}
    	return result;
    }
    
    private List<Inputs> createVariants(String key, TimeSlicedContent slices) {
    	var result = new ArrayList<Inputs>(slices.getSlices().size());
		for(var slice:slices.getSlices()) {
			var content = new LinkedHashMap<String, LCContent>();
			content.put(key, slice.getContent());
			var variant = new Inputs(slice.getTime(), content);
			result.add(variant);
		}
		return result;
    }
    
    @Getter
    @RequiredArgsConstructor
    public static class Inputs {
    	private final TimeRange time;
    	private final SequencedMap<String, LCContent> inputs;
    	
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
