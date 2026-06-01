package io.github.pfwikis.layercompiler.steps.model;

import java.util.ArrayList;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class LCStepSlicing extends LCStepAbstract {

	@Override
	protected TimeSlicedContent executeInternal() throws Exception {
		var variants = createVariants();
    	var results = new ArrayList<TimeSlice>();
    	RangeSet<Integer> noData = TreeRangeSet.create();
    	noData.add(Range.all());
    	
    	for(var variant:variants) {
    		var result = process(variant);
    		for(var slice:result.getSlices()) {
    			var time = slice.getTime().intersection(variant.getTime());
    			if(!noData.encloses(time.toGuavaRange()))
    				throw new IllegalStateException("We have multiple slices for the same time area");
    			noData.remove(time.toGuavaRange());
    			var resultCopy = LCContent.from(slice.getContent().toBytes());
    			resultCopy.setName(getName()+"."+getStep()+"."+time);
                results.add(TimeSlice.from(time, resultCopy));
    		}
    	}
    	return new TimeSlicedContent(results);
	}
	
	protected abstract TimeSlicedContent process(Inputs in) throws Exception;
}
