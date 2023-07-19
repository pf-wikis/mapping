package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.run.Tools;

public class Subtract extends LCStep {

    @Override
    public byte[] process() throws Exception {
        return Tools.mapshaper(getInput(),
            "-dissolve2",
            "-explode",
            "-erase", getInput("subtrahend")
        );
    }

}
