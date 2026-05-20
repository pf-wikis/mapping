package io.github.pfwikis.layercompiler.steps.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
public abstract class LCStepMergingTime extends LCStepAbstract {

	@Override
	protected TimeSlicedContent executeInternal() throws Exception {
		var variants = createVariants();
    	var merged = new LinkedHashMap<String, LCContent>();
    	for(var key:getInputMapping().keySet()) {
			merged.put(key, merge(variants, key));
    	}
		
    	var res = process(new Inputs(Range.all(), merged));
    	return new TimeSlicedContent(List.of(TimeSlice.from(Range.all(), res)));
	}

	private LCContent merge(List<Inputs> variants, String key) {
		Map<Feature, TreeRangeSet<Integer>> geometry = new HashMap<>();
		var result = new FeatureCollection();
		
		int total = 0;
		for(var slice:variants) {
			for(var f:slice.getInput(key).toFeatureCollection().getFeatures()) {
				total++;
				geometry.computeIfAbsent(f, k->TreeRangeSet.create())
					.add(slice.getTime());
			}
		}
		log.info("Merged {} time sliced geometry into {} for key {}", total, geometry.size(), key);
		
		for(var geom:geometry.entrySet()) {
			for(var time:geom.getValue().asRanges()) {
				Feature f = geom.getKey().copy();
				if(time.hasLowerBound())
					f.getProperties().setTimeStart(time.lowerEndpoint());
				if(time.hasUpperBound())
					f.getProperties().setTimeEnd(time.upperEndpoint());
				result.getFeatures().add(f);
			}
		}
		return LCContent.from(result);
	}

	protected abstract LCContent process(Inputs in) throws Exception;
}
