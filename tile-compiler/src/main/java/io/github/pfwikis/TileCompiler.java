package io.github.pfwikis;

import java.io.File;

import io.github.pfwikis.run.Runner;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TileCompiler {

    private CLIOptions options;
    private File geo;


    public void run(CLIOptions options) throws Exception {
        this.options = options;

        //create target folders
        options.targetDirectory().mkdirs();

        Runner.setMaximumParallelism(Math.min(8, Runtime.getRuntime().availableProcessors()));
        new LayersCompiler(this).compile();
    }
}
