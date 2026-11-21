package io.github.pfwikis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

import io.github.pfwikis.model.FeatureCollection;

class MinimumBoundingRectangleTest {

	@Test
	void matchesQgisAngleForRepresentativeLabelPolygons() throws Exception {
		FeatureCollection features;
		try(InputStream in = getClass().getResourceAsStream("/angle/qgis-angle-polygons.geojson")) {
			assertThat(in).isNotNull();
			features = Jackson.JSON.readValue(in, FeatureCollection.class);
		}

		// Actual dissolved label polygons from the mapping pipeline, including
		// a triangle that exercises QGIS' equal-area rectangle tie-breaking.
		assertThat(features.getFeatures()).hasSizeGreaterThanOrEqualTo(50);

		for(var feature : features.getFeatures()) {
			var extra = feature.getProperties().getUnknownFields();
			var id = extra.get("id").stringValue();
			var expectedWidth = extra.get("expectedWidth").doubleValue();
			var expectedHeight = extra.get("expectedHeight").doubleValue();
			var expectedAngle = extra.get("expectedAngle").doubleValue();

			var actual = MinimumBoundingRectangle.calculate(feature.getGeometry());

			assertThat(actual.width()).as("width for %s", id).isCloseTo(expectedWidth, org.assertj.core.data.Offset.offset(1e-5));
			assertThat(actual.height()).as("height for %s", id).isCloseTo(expectedHeight, org.assertj.core.data.Offset.offset(1e-5));
			assertThat(actual.angle()).as("angle for %s", id).isCloseTo(expectedAngle, org.assertj.core.data.Offset.offset(1e-5));

			assertThat(toLabelAngle(actual.width(), actual.height(), actual.angle()))
				.as("label angle for %s", id)
				.isEqualTo(toLabelAngle(expectedWidth, expectedHeight, expectedAngle));
		}
	}

	private static int toLabelAngle(double width, double height, double angle) {
		width = round6(width);
		height = round6(height);
		angle = round6(angle);
		if(height / width < 1.5d) {
			return 0;
		}
		int rounded = BigDecimal.valueOf(angle)
			.subtract(BigDecimal.valueOf(90))
			.setScale(0, RoundingMode.HALF_UP)
			.intValueExact();
		return rounded < 10 && rounded > -10 ? 0 : rounded;
	}

	private static double round6(double value) {
		return BigDecimal.valueOf(value)
			.setScale(6, RoundingMode.HALF_UP)
			.doubleValue();
	}
}
