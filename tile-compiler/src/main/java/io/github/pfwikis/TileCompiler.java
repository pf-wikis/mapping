package io.github.pfwikis;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.run.Runner;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TileCompiler {

    public void run(CLIOptions options) throws Exception {
    	Ctx.INSTANCE.setOptions(options);

        //create target folders
        options.targetDirectory().mkdirs();

        Runner.setMaximumParallelism(Math.min(4, Runtime.getRuntime().availableProcessors()));
        new LayersCompiler().compile();
    }
}
