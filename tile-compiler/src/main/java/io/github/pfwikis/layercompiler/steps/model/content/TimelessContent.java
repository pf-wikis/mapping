package io.github.pfwikis.layercompiler.steps.model.content;

import io.github.pfwikis.layercompiler.steps.model.Time.ContentState;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TimelessContent extends Content {

	private final GeoData data;
	
	@Override
	public ContentState getTimeState() {
		return ContentState.TIMELESS;
	}

	@Override
	public MergedContent asMerged() {
		return new MergedContent(data);
	}

	@Override
	public TimeSlicedContent asSliced() {
		return Content.slicedFromSingleAlwaysSlice(data);
	}
}
