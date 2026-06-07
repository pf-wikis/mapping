package io.github.pfwikis.layercompiler.steps.time;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.util.time.TimeRange;
import lombok.Setter;

@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class TimeMetaLimit extends StepExecutor {
	
	private TimeRange time;

    @Override
    public Content process(Inputs in) throws IOException {
    	if(!time.intersects(in.getTime()))
    		return Content.empty();
    	var fc = in.getInput();
    	return Content.slice(
    		in.getTime().intersection(time),
    		fc
    	);
    }
}
