package io.github.pfwikis.util;

import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.IntPredicate;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;

import io.github.pfwikis.util.time.TimeRange;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class TimeMap<V> {
	
	private final RangeMap<Integer, V> map;

	public static <V> TimeMap<V> create() {
		return new TimeMap<>(TreeRangeMap.create());
	}

	public TimeMap<V> subTimeMap(TimeRange time) {
		return new TimeMap<>(map.subRangeMap(time.toGuavaRange()));
	}

	public boolean isEmpty() {
		return map.asMapOfRanges().isEmpty();
	}

	public void put(TimeRange time, V value) {
		map.putCoalescing(time.toGuavaRange(), value);
	}

	public List<? extends Entry<TimeRange, V>> entries() {
		return map.asMapOfRanges().entrySet()
			.stream()
			.map(e->Pair.of(TimeRange.from(e.getKey()), e.getValue()))
			.toList();
	}

	public List<TimeRange> ranges() {
		return map.asMapOfRanges().keySet()
			.stream()
			.map(TimeRange::from)
			.toList();
	}

	public List<V> values() {
		return List.copyOf(map.asMapOfRanges().values());
	}

	public void merge(TimeRange time, V value, BinaryOperator<V> merger) {
		map.merge(time.toGuavaRange(), value, merger);
		var copy = TreeRangeMap.copyOf(map);
		map.clear();
		for(var e:copy.asMapOfRanges().entrySet()) {
			map.putCoalescing(e.getKey(), e.getValue());
		}
	}

	public int size() {
		return entries().size();
	}
}
