package io.github.pfwikis;


import com.beust.jcommander.Parameter;

import lombok.Getter;

@Getter
public class CLIOptions {
    @Parameter(names = "-maxZoom")
    private int maxZoom=7;
    @Parameter(names = "-useBuildShortcut")
    private boolean useBuildShortcut=false;
    @Parameter(names = "-dataPath")
    private String dataPath = "data";
    @Parameter(names = "-prodDetail")
    private boolean prodDetail = false;
}
