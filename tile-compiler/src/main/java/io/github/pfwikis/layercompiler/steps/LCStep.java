package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
    private Map<String, String> inputMapping = new HashMap<>();

    public abstract LCContent process() throws Exception;

    @Override
    public LCContent execute() {
    	var oldName = Thread.currentThread().getName();
        try {
        	Thread.currentThread().setName(name+"."+step);
            return process();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
