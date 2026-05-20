package io.github.pfwikis.layercompiler.steps.model;

import java.util.ArrayList;

import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class LCStepSlicing extends LCStepAbstract {

	@Override
	protected TimeSlicedContent executeInternal() throws Exception {
		var variants = createVariants();
    	var results = new ArrayList<TimeSlice>();
    	
    	for(var variant:variants) {
    		var result = process(variant);
    		for(var slice:result.getSlices()) {
    			var time = slice.getTime().intersection(variant.getTime());
    			var resultCopy = LCContent.from(slice.getContent().toBytes());
    			resultCopy.setName(getName()+"."+getStep()+"."+time);
                results.add(TimeSlice.from(time, resultCopy));
    		}
    		
    	}
    	return new TimeSlicedContent(results);
	}
	
	protected abstract TimeSlicedContent process(Inputs in) throws Exception;
}
