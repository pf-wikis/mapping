package io.github.pfwikis.layercompiler.steps.model.content;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.layercompiler.steps.model.Time.ContentState;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.FeatureCollection.FCProperties;
import io.github.pfwikis.util.time.TimeRange;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Getter @Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TimeSlicedContent extends Content {
	private final List<TimeSlice> slices;
	@Getter(lazy = true) @Accessors(fluent = true)
	private final MergedContent asMerged = merge();
	
	@Getter
	@ToString(of = "time")
	@RequiredArgsConstructor
    public static class TimeSlice {
    	private final TimeRange time;
    	private final GeoData data;
    }

	@Override
	public ContentState getTimeState() {
		return ContentState.SLICED;
	}
	
	@Override
	public TimeSlicedContent asSliced() {
		return this;
	}

	private MergedContent merge() {
		Map<Feature, TreeRangeSet<Integer>> geometry = new HashMap<>();
		var result = new FeatureCollection();
		FCProperties mergedProps = null;
		
		int total = 0;
		
		//because the same LCContent could be in multiple slices we resolve this first
		var contentsToTime = new IdentityHashMap<GeoData, TreeRangeSet<Integer>>(slices.size());
		slices.forEach(slice->contentsToTime.computeIfAbsent(slice.getData(), _->TreeRangeSet.create())
				.add(slice.getTime().toGuavaRange()));
		
		for(var contentAndTime:contentsToTime.entrySet()) {
			if(contentAndTime.getKey().isEmpty()) continue;
			var fc = contentAndTime.getKey().toFeatureCollection();
			var props = fc.getProperties();
			if(mergedProps==null)
				mergedProps = props;
			else if(!mergedProps.equals(props))
				throw new IllegalStateException("Time merging with different properties: "+mergedProps+" and "+props);
			
			for(var f:fc.getFeatures()) {
				total++;
				var time=f.getProperties().getTime();
				f.getProperties().setTime(null);
				geometry.computeIfAbsent(f, _->TreeRangeSet.create())
					.addAll(contentAndTime.getValue().subRangeSet(time.toGuavaRange()));
			}
		}
		result.setProperties(mergedProps);
		log.info("Merged {} time sliced geometry into {}", total, geometry.size());
		
		for(var geom:geometry.entrySet()) {
			for(var time:geom.getValue().asRanges()) {
				Feature f = geom.getKey().copy();
				f.getProperties().setTime(TimeRange.from(time));
				result.getFeatures().add(f);
			}
		}
		
		return Content.merged(GeoData.from(result));
	}

	public Content asTimelessIfPossible() {
		if(slices.size() == 1 && slices.getFirst().getTime().equals(TimeRange.always())) {
			return new TimelessContent(slices.getFirst().getData());
		}
		return this;
	}
}
