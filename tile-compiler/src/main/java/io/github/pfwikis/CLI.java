package io.github.pfwikis;

import java.io.IOException;

import com.beust.jcommander.JCommander;

public class CLI {
    public static void main(String[] args) throws IOException {
        var options = new CLIOptions();
        var cleanOptions = new Object();
        var jc = JCommander.newBuilder()
            .addCommand("compileTiles", options)
            .addCommand("clean", cleanOptions)
            .build();
        jc.parse(args);

        if("clean".equals(jc.getParsedCommand())) {
            new Cleaner().run();
        }
        else {
            new TileCompiler().run(options);
        }
    }
}
