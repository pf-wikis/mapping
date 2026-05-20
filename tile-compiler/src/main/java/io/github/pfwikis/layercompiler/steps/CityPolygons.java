package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class CityPolygons extends LCStep {

	@Override
	public LCContent process(Inputs in) throws IOException {
		return Tools.mapshaper(this, in.getInput(),
			"-filter", "Boolean(city)",
			"-dissolve2", "city"
		);
	}
}
