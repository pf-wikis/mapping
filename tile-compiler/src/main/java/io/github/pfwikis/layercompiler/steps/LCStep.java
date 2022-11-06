package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.pfwikis.CLIOptions;
import io.github.pfwikis.Tools;
import io.github.pfwikis.layercompiler.LayerCompiler;
import lombok.Value;

public abstract class LCStep {

    public abstract File process(Ctx ctx, File f) throws IOException;

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
        private final File input;
    }


    private static final File TMP_DIR;
    private static final AtomicInteger TMP_COUNTER = new AtomicInteger();

    static {
        try {
            TMP_DIR = Tools.tmpDir();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected File tmpGeojson() {
        return new File(TMP_DIR, TMP_COUNTER.getAndIncrement()+".geojson");
    }

}
