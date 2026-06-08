package io.github.pfwikis.layercompiler.steps.model.content;

import java.util.Optional;

import io.github.pfwikis.layercompiler.steps.model.Time.ContentState;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.layercompiler.steps.time.TimeSlicer;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.time.TimeRange;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class MergedContent implements MergedOrTimelessContent {

	private final GeoData data;
	@Getter(lazy = true) @Accessors(fluent = true)
	private final TimeSlicedContent asSliced = slice();
	
	@Override
	public ContentState getTimeState() {
		return ContentState.MERGED;
	}

	@Override
	public MergedOrTimelessContent asMergedOrTimeless() {
		return this;
	}
	
	public TimeSlicedContent slice() {
		var fc = data.toFeatureCollection();
		var barriers = TimeSlicer.extractBarriers(fc);
		
		return new TimeSlicedContent(
			TimeSlicer.barriersToSlices(barriers)
				.stream()
				.map(time -> {
					var rfc = new FeatureCollection();
					
					for(var f:fc.getFeatures()) {
						if(shouldContain(time, f)) {
							Feature c = f.copy();
							c.getProperties().setTime(time.intersection(
								Optional.ofNullable(c.getProperties().getTime())
									.orElse(TimeRange.always())
							));
							rfc.getFeatures().add(c);
						}
					}
					return new TimeSlice(time, GeoData.from(rfc));
				})
				.toList()
		);
	}
	
	private boolean shouldContain(TimeRange time, Feature f) {
		TimeRange fRange = f.getProperties().getTime();
		return time.intersects(fRange);
	}

}
