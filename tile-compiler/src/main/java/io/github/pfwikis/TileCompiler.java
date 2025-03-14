package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.ToolVariant;
import io.github.pfwikis.run.Tools;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TileCompiler {

    private CLIOptions options;
    private File geo;
    private File targetDir;
    private File targetRoot;


    public void run(CLIOptions options) throws Exception {
        this.options = options;

        //create target folders
        targetDir.mkdirs();

        new LayersCompiler(
    		this,
    		Math.min(4, Runtime.getRuntime().availableProcessors())
		).compile();
    }
}
