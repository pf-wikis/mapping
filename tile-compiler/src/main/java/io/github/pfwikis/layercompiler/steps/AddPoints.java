package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class AddPoints extends LCStep {

    @Override
    public LCContent process(Inputs in) throws Exception {
        return Tools.mapshaper2(this, in.getInput(), in.getInput("summand"), "combine-files",
            "-merge-layers", "force"
        );
    }

}
