package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class DissolveByLabel extends StepExecutor {
	
	@Override
	protected Content process(Inputs in) throws Exception {
		return Content.derivedFrom(in, Tools.mapshaper(this, in.getInput(),
			"-require", "crypto",
			"-each", "mid=label||crypto.randomUUID()",
			"-dissolve", "mid", "copy-fields=label",
			"-filter-fields", "label"
		));
	}
}
