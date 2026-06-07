package io.github.pfwikis.util.time;

import java.util.function.IntConsumer;

import com.google.common.collect.Range;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class TimeRange {
	
	private final Integer timeStart;
	private final Integer timeEnd;
	
	private static final TimeRange ALWAYS = new TimeRange(null, null);
	
	public static TimeRange always() {
		return ALWAYS;
	}
	
	public static TimeRange from(Range<Integer> time) {
		Integer start = time.hasLowerBound()?time.lowerEndpoint():null;
		Integer end = time.hasUpperBound()?time.upperEndpoint():null;
		return new TimeRange(start, end);
	}
	
	public void forEachBarrier(IntConsumer cons) {
		if(timeStart != null) cons.accept(timeStart);
		if(timeEnd != null) cons.accept(timeEnd);
	}

	public boolean intersects(TimeRange fRange) {
		return toGuavaRange().isConnected(fRange.toGuavaRange())
				&& !toGuavaRange().intersection(fRange.toGuavaRange()).isEmpty();
	}

	public Range<Integer> toGuavaRange() {
		Range<Integer> range;
		if(timeStart==null) {
			if(timeEnd==null)
				range = Range.all();
			else
				range = Range.lessThan(timeEnd);
		}
		else {
			if(timeEnd==null)
				range = Range.atLeast(timeStart);
			else
				range = Range.closedOpen(
					timeStart,
					timeEnd
				);
		}
		return range;
	}

	public boolean hasUpperBound() {
		return timeEnd != null;
	}
	
	public boolean hasLowerBound() {
		return timeStart != null;
	}

	public TimeRange intersection(TimeRange time) {
		return TimeRange.from(toGuavaRange().intersection(time.toGuavaRange()));
	}
}
