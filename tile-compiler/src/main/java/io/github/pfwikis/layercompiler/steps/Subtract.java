package io.github.pfwikis.layercompiler.steps;

import java.util.Collections;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;
import lombok.Setter;

@Setter
public class Subtract extends LCStep {
	
	private boolean keepName = false;

    @Override
    public LCContent process() throws Exception {
        return Tools.mapshaper(this, getInput(),
            "-dissolve2",
            keepName?"Name":Collections.emptyList(),
            "-explode",
            "-erase", getInput("subtrahend")
        );
    }

}
