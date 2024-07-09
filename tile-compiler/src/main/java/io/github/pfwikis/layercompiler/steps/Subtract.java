package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class Subtract extends LCStep {

    @Override
    public LCContent process() throws Exception {
        return Tools.mapshaper(this, getInput(),
            "-dissolve2",
            "-explode",
            "-erase", getInput("subtrahend")
        );
    }

}
