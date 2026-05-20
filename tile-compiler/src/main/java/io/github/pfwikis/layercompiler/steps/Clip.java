package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class Clip extends LCStep {

	@Override
	public LCContent process(Inputs in) throws Exception {
		return Tools.mapshaper(this, in.getInput(),
            "-clip", in.getInput("mask")
        );
	}
}
