package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.ANY)
public class AddPoints extends StepExecutor {

    @Override
    public Content process(Inputs in) throws Exception {
        return Content.derivedFrom(
        	in,
        	Tools.mapshaper2(this, in.getInput(), in.getInput("summand"), "combine-files",
        			"-merge-layers", "force"
        	)
        );
    }

}
