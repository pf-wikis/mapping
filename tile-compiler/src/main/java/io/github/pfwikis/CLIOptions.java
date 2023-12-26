package io.github.pfwikis;


import java.io.File;

import com.beust.jcommander.Parameter;

import lombok.Getter;

@Getter
public class CLIOptions {
    @Parameter(names = "-maxZoom")
    private int maxZoom=8;
    @Parameter(names = "-useBuildShortcut")
    private boolean useBuildShortcut=false;
    @Parameter(names = "-dataHash")
    private String dataHash = "data";
    @Parameter(names = "-prodDetail")
    private boolean prodDetail = false;
    @Parameter(names = "-mappingDataFile")
    private File mappingDataFile = new File("../../mapping-data/mapping-data.gpkg");
}
