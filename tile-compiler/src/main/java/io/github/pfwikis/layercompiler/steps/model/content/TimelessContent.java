package io.github.pfwikis.layercompiler.steps.model.content;

import io.github.pfwikis.layercompiler.steps.model.Time.ContentState;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TimelessContent implements MergedOrTimelessContent {

	private final GeoData data;
	
	@Override
	public ContentState getTimeState() {
		return ContentState.TIMELESS;
	}

	@Override
	public TimelessContent asMergedOrTimeless() {
		return this;
	}

	@Override
	public TimeSlicedContent asSliced() {
		return Content.slicedFromSingleAlwaysSlice(data);
	}
}
