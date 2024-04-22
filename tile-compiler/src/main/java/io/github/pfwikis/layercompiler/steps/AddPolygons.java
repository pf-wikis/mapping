package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class AddPolygons extends LCStep {

    @Override
    public LCContent process() throws Exception {
        return Tools.mapshaper2(getInput(), getInput("summand"), "combine-files",
            "-merge-layers", "force",
            "-dissolve2",
            "-explode"
        );
    }

}
