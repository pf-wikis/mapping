package io.github.pfwikis.layercompiler.steps.rivers;

import io.github.pfwikis.layercompiler.steps.LCStep;
import io.github.pfwikis.run.Tools;

public class RiverLabels extends LCStep {

    @Override
    public byte[] process() throws Exception {
        return Tools.mapshaper(getInput(),
            "-clean",
            "-dissolve", "Name",
            "-filter", "Name !== null",
            "-each", "filterMinzoom=5"
        );
    }

}
