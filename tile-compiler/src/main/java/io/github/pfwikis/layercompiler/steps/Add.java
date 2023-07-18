package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.run.Tools;

public class Add extends LCStep {

    @Override
    public byte[] process() throws Exception {
        return Tools.mapshaper2(getInput(), getInput("summand"), "combine-files",
            "-merge-layers", "force",
            "-dissolve2",
            "-explode"
        );
    }

}
