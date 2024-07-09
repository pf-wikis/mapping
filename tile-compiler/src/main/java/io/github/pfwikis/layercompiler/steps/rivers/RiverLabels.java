package io.github.pfwikis.layercompiler.steps.rivers;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class RiverLabels extends LCStep {

    @Override
    public LCContent process() throws Exception {
        return Tools.mapshaper(this, getInput(),
            "-clean",
            "-dissolve", "Name",
            "-filter", "Name !== null",
            "-each", "filterMinzoom=5"
        );
    }

}
