package io.github.pfwikis.layercompiler.steps.model.content;

import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.util.time.TimeRange;

public abstract class Content {

	public abstract Time.ContentState getTimeState();

	
	public abstract MergedContent asMerged();
	
	public abstract TimeSlicedContent asSliced();
	
	/* ------------------- creator methods ----------------------------*/
	public static Content derivedFrom(Inputs in, GeoData data) {
		return switch(in.getTimeState()) {
			case MERGED -> new MergedContent(data);
			case TIMELESS -> new TimelessContent(data);
		};
	}

	public static TimeSlicedContent sliced(List<TimeSlice> slices) {
		return new TimeSlicedContent(slices);
	}
	
	public static TimeSlicedContent slice(TimeRange time, GeoData data) {
		return new TimeSlicedContent(List.of(new TimeSlice(time, data)));
	}
	
	public static TimeSlicedContent slicedFromSingleAlwaysSlice(GeoData data) {
		return new TimeSlicedContent(List.of(new TimeSlice(TimeRange.always(), data)));
	}

	public static TimelessContent timeless(GeoData data) {
		return new TimelessContent(data);
	}

	private static final Content EMPTY = timeless(GeoData.empty());
	public static Content empty() {
		return EMPTY;
	}

	public static MergedContent merged(GeoData data) {
		return new MergedContent(data);
	}
}
