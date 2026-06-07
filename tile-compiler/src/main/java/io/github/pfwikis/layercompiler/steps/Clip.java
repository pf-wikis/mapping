package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class Clip extends StepExecutor {

	@Override
	public Content process(Inputs in) throws Exception {
		return Content.timeless(Tools.mapshaper(this, in.getInput(),
            "-clip", in.getInput("mask")
        ));
	}
}
