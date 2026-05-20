package io.github.pfwikis.layercompiler.steps.model;

import java.util.ArrayList;

import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class LCStepLatestOnly extends LCStep {

	@Override
	protected TimeSlicedContent executeInternal() throws Exception {
		var variants = createVariants();
    	var results = new ArrayList<TimeSlice>();
    	
    	for(var variant:variants) {
    		if(variant.getTime().hasUpperBound())
    			continue;
    		var result = process(variant);
            result.setName(getName()+"."+getStep()+"."+variant.getTime());
            results.add(TimeSlice.from(variant.getTime(), result));
    	}
    	return new TimeSlicedContent(results);
	}

	protected abstract LCContent process(Inputs in) throws Exception;
}
