package io.github.pfwikis.layercompiler.steps.model;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

import com.github.dexecutor.core.task.Task;

import io.github.pfwikis.CLIOptions;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Getter @Setter
public abstract class LCStep extends Task<String, LCContent> {

    protected Ctx ctx;
    private String name;
    private String step;
    private int numberOfDependents;
    private SequencedMap<String, String> inputMapping = new LinkedHashMap<>();

    public abstract LCContent process() throws Exception;

    @Override
    public LCContent execute() {
    	var oldName = Thread.currentThread().getName();
        try {
        	Thread.currentThread().setName(name+"."+step);
            var result = process();
            result.setNumberOfValidUses(numberOfDependents);
            result.setName(name+"."+step);
            for(var input:inputMapping.values()) {
            	this.getResult(input).getResult().finishUsage();
            }
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Error in "+name+"."+step, t);
        } finally {
        	Thread.currentThread().setName(oldName);
        }
    }

    protected LCContent getInput() {
        return getInput("in");
    }

    protected LCContent getInput(String key) {
        return this.getResult(inputMapping.get(key)).getResult();
    }
    
    protected SequencedMap<String, LCContent> getInputs() {
    	var result = new LinkedHashMap<String, LCContent>();
    	for(var e:inputMapping.entrySet()) {
    		result.put(e.getKey(), this.getResult(e.getValue()).getResult());
    	}
    	return result;
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
