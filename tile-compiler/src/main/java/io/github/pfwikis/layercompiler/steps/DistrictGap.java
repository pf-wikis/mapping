package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Tools;

public class DistrictGap extends LCStep {

    @Override
    public LCContent process() throws IOException {
        var innerLines = Tools.mapshaper(getInput(),
            "-clean",
            "-snap", "precision=0.0001",
            "-innerlines", "-dissolve"
        );

        var bufferedLines = Tools.qgis("native:buffer", innerLines,
            "--DISTANCE=0.0004",
            "--SEGMENTS=5",
            "--END_CAP_STYLE=0",
            "--JOIN_STYLE=0",
            "--MITER_LIMIT=2",
            "--DISSOLVE=true"
        );

        return Tools.qgis("native:difference", getInput(), new Runner.TmpGeojson("--OVERLAY=", bufferedLines));
    }


}
