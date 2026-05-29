package io.github.pfwikis.layercompiler.steps.model;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.FeatureCollection.FCProperties;
import io.github.pfwikis.util.time.TimeRange;
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
		
    	var res = process(new Inputs(TimeRange.always(), merged));
    	return new TimeSlicedContent(List.of(TimeSlice.from(TimeRange.always(), res)));
	}

	private LCContent merge(List<Inputs> variants, String key) {
		Map<Feature, TreeRangeSet<Integer>> geometry = new HashMap<>();
		var result = new FeatureCollection();
		FCProperties mergedProps = null;
		
		int total = 0;
		
		//because the same LCContent could be in multiple slices we resolve this first
		var contentsToTime = new IdentityHashMap<LCContent, TreeRangeSet<Integer>>(variants.size());
		variants.forEach(slice->contentsToTime.computeIfAbsent(slice.getInput(key), _->TreeRangeSet.create())
				.add(slice.getTime().toGuavaRange()));
		
		for(var contentAndTime:contentsToTime.entrySet()) {
			var fc = contentAndTime.getKey().toFeatureCollection();
			var props = fc.getProperties();
			props.setTime(TimeRange.always());
			if(mergedProps==null)
				mergedProps = props;
			else if(!mergedProps.equals(props))
				throw new IllegalStateException("Time merging with different properties: "+mergedProps+" and "+props);
			
			for(var f:fc.getFeatures()) {
				total++;
				geometry.computeIfAbsent(f, _->TreeRangeSet.create())
					.addAll(contentAndTime.getValue().subRangeSet(f.getProperties().getTime().toGuavaRange()));
			}
		}
		result.setProperties(mergedProps);
		log.info("Merged {} time sliced geometry into {} for key {}", total, geometry.size(), key);
		
		for(var geom:geometry.entrySet()) {
			for(var time:geom.getValue().asRanges()) {
				Feature f = geom.getKey().copy();
				f.getProperties().setTime(TimeRange.from(time));
				result.getFeatures().add(f);
			}
		}
		
		return LCContent.from(result);
	}

	protected abstract LCContent process(Inputs in) throws Exception;
}
