package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class ClipPolygons extends LCStep {

    @Override
    public LCContent process() throws Exception {
        return Tools.mapshaper(this, getInput(),
            "-clip", getInput("mask")
        );
    }
}
