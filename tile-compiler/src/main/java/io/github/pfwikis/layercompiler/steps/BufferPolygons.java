package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class BufferPolygons extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
        var buffered = Tools.qgis(this, "native:buffer", in.getInput(),
            "--DISTANCE=0.5",
            "--SEGMENTS=20",
            "--END_CAP_STYLE=0", "--JOIN_STYLE=0", "--MITER_LIMIT=2",
            "--DISSOLVE=true");
        var reduced = Tools.mapshaper(this, buffered, "-dissolve", "-filter-fields", "-explode", "-simplify", "percentage=0.3", "keep-shapes");
        var smooth = Tools.qgis(this, "native:smoothgeometry", reduced,
            "--ITERATIONS=3",
            "--OFFSET=0.3",
            "--MAX_ANGLE=180"
        );
        //var negative = Tools.mapshaper0("-rectangle", "bbox=-138,-90,222,90", "-erase", smooth);
        
        return Content.timeless(smooth);
    }

}
