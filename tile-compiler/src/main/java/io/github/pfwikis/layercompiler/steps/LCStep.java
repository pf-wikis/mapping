package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.CLIOptions;
import io.github.pfwikis.layercompiler.LayerCompiler;
import lombok.Value;

public abstract class LCStep {

    public abstract byte[] process(Ctx ctx, byte[] f) throws IOException;

    protected void createNewLayer(Ctx ctx) {
        var lc = new LayerCompiler(ctx);
        lc.init();
        lc.compile();
    }

    @Value
    public static class Ctx {
        private final String name;
        private final CLIOptions options;
        private final File geo;
        private final byte[] input;
    }


}
