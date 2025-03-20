package io.github.pfwikis;

import com.beust.jcommander.JCommander;

public class CLI {
    public static void main(String[] args) throws Exception {
        var options = new CLIOptions();
        var cleanOptions = new Object();
        var jc = JCommander.newBuilder()
            .addCommand("compileTiles", options)
            .addCommand("clean", cleanOptions)
            .build();
        jc.parse(args);

        new TileCompiler().run(options);
    }
}
