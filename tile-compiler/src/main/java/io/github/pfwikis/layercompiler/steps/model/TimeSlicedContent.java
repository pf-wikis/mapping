package io.github.pfwikis.layercompiler.steps.model;

import java.util.List;

import io.github.pfwikis.model.Feature;
import io.github.pfwikis.util.time.TimeRange;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
public class TimeSlicedContent {
	private final List<TimeSlice> slices;
	
	@Getter
	@ToString(of = "time")
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TimeSlice {
    	private final TimeRange time;
    	@Setter
    	private LCContent content;

		public boolean shouldContain(Feature f) {
			TimeRange fRange = f.getProperties().getTime();
			return time.intersects(fRange);
		}
		
		public static TimeSlice from(TimeRange time) {
			return new TimeSlice(time);
		}

		public static TimeSlice from(TimeSlice slice) {
			return new TimeSlice(slice.time);
		}

		public static TimeSlice from(TimeRange time, LCContent content) {
			return new TimeSlice(time, content);
		}
    }
}
