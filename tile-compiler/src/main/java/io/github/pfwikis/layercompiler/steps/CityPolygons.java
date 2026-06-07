package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class CityPolygons extends StepExecutor {

	@Override
	public Content process(Inputs in) throws IOException {
		return Content.timeless(Tools.mapshaper(this, in.getInput(),
			"-filter", "Boolean(city)",
			"-dissolve2", "city"
		));
	}
}
