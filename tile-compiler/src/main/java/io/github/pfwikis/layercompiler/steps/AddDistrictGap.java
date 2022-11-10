package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.Tools;

public class AddDistrictGap extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        var innerLines = tmpGeojson();
        Tools.mapshaper(f, innerLines,
            "-clean",
            "-snap", "precision=0.0001",
            "-innerlines", "-dissolve"
        );

        var bufferedLines = tmpGeojson();
        Tools.qgis("native:buffer", innerLines, bufferedLines,
            "--DISTANCE=0.0004",
            "--SEGMENTS=5",
            "--END_CAP_STYLE=0",
            "--JOIN_STYLE=0",
            "--MITER_LIMIT=2",
            "--DISSOLVE=true"
        );

        var result = tmpGeojson();
        Tools.qgis("native:difference", f, result, "--OVERLAY="+bufferedLines);
        return result;
    }


}
