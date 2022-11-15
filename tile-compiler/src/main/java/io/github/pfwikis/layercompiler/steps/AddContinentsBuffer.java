package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.LayerCompiler;
import io.github.pfwikis.run.Tools;

public class AddContinentsBuffer extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        var buffered = Tools.qgis("native:buffer", f,
            "--DISTANCE=1",
            "--SEGMENTS=20",
            "--END_CAP_STYLE=0", "--JOIN_STYLE=0", "--MITER_LIMIT=2",
            "--DISSOLVE=true");
        var reduced = Tools.mapshaper(buffered, "-filter-fields", "-explode");
        new LayerCompiler(new Ctx("continents_buffer", ctx.getOptions(), ctx.getGeo(), reduced)).compile();
        return f;
    }

}
