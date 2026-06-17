package io.github.pfwikis.util;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import io.github.pfwikis.util.time.TimeRange;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class TimeSet {

	private final RangeSet<Integer> set;

	public static TimeSet create() {
		return new TimeSet(TreeRangeSet.create());
	}
	
	public static TimeSet create(TimeSet toCopy) {
		return new TimeSet(toCopy.set);
	}
	
	public static TimeSet create(List<TimeRange> list) {
		var res = new TimeSet(TreeRangeSet.create());
		list.forEach(res::add);
		return res;
	}

	public void add(TimeRange time) {
		set.add(time.toGuavaRange());
	}

	public TimeSet subTimeSet(TimeRange time) {
		return new TimeSet(set.subRangeSet(time.toGuavaRange()));
	}

	public void addAll(TimeSet value) {
		set.addAll(value.set);
	}

	public Set<TimeRange> asRanges() {
		return set.asRanges().stream().map(TimeRange::from).collect(Collectors.toSet());
	}

	public void removeAll(Collection<TimeRange> ranges) {
		ranges.forEach(r->set.remove(r.toGuavaRange()));
	}
}
