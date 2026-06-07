package io.github.pfwikis.layercompiler.steps;

import java.util.Collections;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;
import lombok.Setter;

@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class Subtract extends StepExecutor {
	
	private String keepField = null;

    @Override
    public Content process(Inputs in) throws Exception {
        return Content.timeless(Tools.mapshaper(this, in.getInput(),
            "-dissolve", keepField!=null?keepField:Collections.emptyList(),
            "-explode",
            "-erase", in.getInput("subtrahend")
        ));
    }

}
