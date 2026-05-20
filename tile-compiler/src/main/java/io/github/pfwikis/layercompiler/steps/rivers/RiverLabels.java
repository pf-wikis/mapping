package io.github.pfwikis.layercompiler.steps.rivers;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class RiverLabels extends LCStep {

    @Override
    public LCContent process(Inputs in) throws Exception {
        return Tools.mapshaper(this, in.getInput(),
            "-clean",
            "-dissolve", "label",
            "-filter", "Boolean(label)",
            "-each", "filterMinzoom=5"
        );
    }

}
