package io.github.pfwikis.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.pfwikis.util.time.TimeRange;

class TimeMapTest {

	@Test
	void testEmpty() {
		var a = TimeMap.<Integer>create();
		assertThat(a.isEmpty()).isEqualTo(true);
		a.put(new TimeRange(4,5), 0);
		assertThat(a.isEmpty()).isEqualTo(false);
		assertThat(a.subTimeMap(new TimeRange(5,6)).isEmpty()).isEqualTo(true);
		assertThat(a.subTimeMap(new TimeRange(4,6)).isEmpty()).isEqualTo(false);
	}
	
	@Test
	void testCoalescing() {
		var a = TimeMap.<Integer>create();
		a.put(TimeRange.always(), 0);
		a.merge(new TimeRange(4, 5), 0, (_,_)->0);
		assertThat(a.size()).isEqualTo(1);
	}

}
