package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class BufferPolygons extends LCStep {

    @Override
    public LCContent process() throws IOException {
        var buffered = Tools.qgis("native:buffer", getInput(),
            "--DISTANCE=0.5",
            "--SEGMENTS=20",
            "--END_CAP_STYLE=0", "--JOIN_STYLE=0", "--MITER_LIMIT=2",
            "--DISSOLVE=true");
        var reduced = Tools.mapshaper(buffered, "-dissolve", "-filter-fields", "-explode", "-simplify", "percentage=0.3", "keep-shapes");
        var smooth = Tools.qgis("native:smoothgeometry", reduced,
            "--ITERATIONS=3",
            "--OFFSET=0.3",
            "--MAX_ANGLE=180"
        );
       return smooth;
    }

}
