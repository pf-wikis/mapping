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
public abstract class LCStep extends Task<String, byte[]> {

    protected Ctx ctx;
    private String name;
    private String step;
    private Map<String, String> inputMapping = new HashMap<>();

    public abstract byte[] process() throws Exception;
/*
    protected void createNewLayer(Ctx ctx) {
        var lc = new LayerCompiler(ctx);
        lc.init();
        lc.compile();
    }*/

    @Override
    public byte[] execute() {
        try {
            return process();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected byte[] getInput() {
        return getInput("in");
    }

    protected byte[] getInput(String key) {
        return this.getResult(inputMapping.get(key)).getResult();
    }

    @Value
    public static class Ctx {
        private final CLIOptions options;
        private final File geo;
    }

    public void init(Ctx ctx) {
        this.ctx = ctx;
    }

}
