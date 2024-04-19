package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.run.Tools;

public class AddPoints extends LCStep {

    @Override
    public LCContent process() throws Exception {
        return Tools.mapshaper2(getInput(), getInput("summand"), "combine-files",
            "-merge-layers", "force"
        );
    }

}
