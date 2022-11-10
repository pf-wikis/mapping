package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Tools;

public class AddDistrictGap extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        var innerLines = Tools.mapshaper(f,
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

        return Tools.qgis("native:difference", f, new Runner.TmpGeojson("--OVERLAY=", bufferedLines));
    }


}
