package io.github.pfwikis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectionTest {

	@ParameterizedTest
	@ValueSource(doubles = {-90, -85, -3, 0, 10, 35.213, 85, 89.99999, 90})
	void testReversability(double in) {
		var out = Projection.mercatorToGeo(Projection.geoToMercator(in));
		assertThat(out).isEqualTo(in, within(0.00000001));
	}
	
	@Test
	void testValueRange() {
		var acc = within(0.00000001);
		var s = new SoftAssertions(); 
		s.assertThat(Projection.geoToMercator(0)).isEqualTo(0, acc);
		s.assertThat(Projection.geoToMercator(45)).isEqualTo(49.29125493511359, acc);
		s.assertThat(Projection.geoToMercator(-45)).isEqualTo(-49.29125493511359, acc);
		s.assertThat(Projection.geoToMercator(85)).isEqualTo(175.11957980289137, acc);
		s.assertThat(Projection.geoToMercator(-85)).isEqualTo(-175.11957980289137, acc);
		s.assertThat(Projection.geoToMercator(90)).isEqualTo(180, acc);
		s.assertThat(Projection.geoToMercator(-90)).isEqualTo(-180, acc);
		
		s.assertAll();
	}
	
	@Test
	void testClamping() {
		var acc = within(0.00000001);
		var s = new SoftAssertions(); 
		s.assertThat(Projection.geoToMercator(100)).isEqualTo(Projection.geoToMercator(90), acc);
		s.assertThat(Projection.geoToMercator(-100)).isEqualTo(Projection.geoToMercator(-90), acc);
		
		s.assertThat(Projection.mercatorToGeo(190)).isEqualTo(Projection.mercatorToGeo(180), acc);
		s.assertThat(Projection.mercatorToGeo(-190)).isEqualTo(Projection.mercatorToGeo(-180), acc);

		s.assertAll();
	}

}
