package io.github.pfwikis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.pfwikis.util.Jackson;
import io.github.pfwikis.util.time.TimeRange;

class PropertiesTest {

	@Test
	void testSerialization() {
		var props = new Properties();
		
		var copy = props.<Properties>copy();
		assertThat(copy).usingRecursiveComparison().isEqualTo(props);
		
		props.setTime(new TimeRange(5,null));
		copy = props.copy();
		assertThat(copy).usingRecursiveComparison().isEqualTo(props);
		
		var json = Jackson.JSON.writeValueAsString(props);
		assertThat(json).isEqualTo("{\"timeStart\":5}");
		
		props.setTime(new TimeRange(4,345));
		copy = props.copy();
		assertThat(copy).usingRecursiveComparison().isEqualTo(props);
	}
	
	@Test
	void testCapitalization() {
		var props = new Properties();
		props.setTime(new TimeRange(4,345));
		
		assertThat(Jackson.JSON.readValue("{\"timeStart\":4, \"timeEnd\": 345}", Properties.class))
			.usingRecursiveComparison().isEqualTo(props);
	}

}
