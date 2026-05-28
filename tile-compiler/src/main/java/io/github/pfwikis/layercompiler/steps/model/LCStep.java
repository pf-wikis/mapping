package io.github.pfwikis.layercompiler.steps.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class LCStep extends LCStepAbstract {

	@Override
	protected TimeSlicedContent executeInternal() throws Exception {
		var variants = createVariants();
		
		//shortcut in case there is only one
		if(variants.size()==1) {
			var variant = variants.getFirst();
			var res = process(variant);
			res.setName(getName()+"."+getStep());
			return new TimeSlicedContent(List.of(TimeSlice.from(
				variant.getTime(),
				res
			)));
		}
		
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = variants
				.stream()
				.map(v-> executor.submit(() -> {
					LCStepAbstract.setThreadName(getName(), getStep(), v.getTime().toString());
					var result = process(v);
					result.setName(getName()+"."+getStep()+"."+v.getTime());
					return TimeSlice.from(v.getTime(), result);
				}))
				.toList();
			var results = new ArrayList<TimeSlice>(futures.size());
		    try {
		        for (var f : futures) {
		            results.add(f.get());
		        }
		        return new TimeSlicedContent(results);
		    } catch (Exception e) {
		    	futures.forEach(f->f.cancel(true));
		    	throw e;
		    }
		}
	}

	protected abstract LCContent process(Inputs in) throws Exception;
}
