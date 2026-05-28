package io.github.pfwikis.layercompiler.steps.model;

import java.util.List;

import com.google.common.collect.Range;

import io.github.pfwikis.model.Feature;
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
    	private final Range<Integer> time;
    	@Setter
    	private LCContent content;

		public boolean shouldContain(Feature f) {
			Range<Integer> fRange = f.getProperties().getTime();
			return time.isConnected(fRange) && !time.intersection(fRange).isEmpty();
		}

		public static Range<Integer> createRange(Integer start, Integer end) {
			Range<Integer> range;
			if(start==null) {
				if(end==null)
					range = Range.all();
				else
					range = Range.lessThan(end);
			}
			else {
				if(end==null)
					range = Range.atLeast(start);
				else
					range = Range.closedOpen(
						start,
						end
					);
			}
			return range;
		}
		
		public static TimeSlice fromRange(Integer start, Integer end) {
			return new TimeSlice(createRange(start, end));
		}

		public static TimeSlice from(TimeSlice slice) {
			return new TimeSlice(slice.time);
		}

		public static TimeSlice from(Range<Integer> time, LCContent content) {
			return new TimeSlice(time, content);
		}
    }
}
