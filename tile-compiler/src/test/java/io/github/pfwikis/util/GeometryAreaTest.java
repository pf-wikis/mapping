package io.github.pfwikis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import io.github.pfwikis.model.FeatureCollection;

class GeometryAreaTest {

	@Test
	void matchesQgisAreaForRepresentativePolygons() throws Exception {
		FeatureCollection features;
		try(InputStream in = getClass().getResourceAsStream("/area/qgis-area-polygons.geojson")) {
			assertThat(in).isNotNull();
			features = Jackson.JSON.readValue(in, FeatureCollection.class);
		}

		// expectedAreaM2 was produced with qgis_process native:fieldcalculator,
		// using --ELLIPSOID=EPSG:7030 (the WGS84 ellipsoid) and $area. Note that
		// the old pipeline passed EPSG:4326, which is a CRS rather than an
		// ellipsoid and makes QGIS fall back to planar area.
		//
		// The fixture contains a few dozen real mapping-data polygons, plus
		// antimeridian and polar cases.
		assertThat(features.getFeatures()).hasSizeGreaterThanOrEqualTo(40);

		for(var feature : features.getFeatures()) {
			var extra = feature.getProperties().getUnknownFields();
			var id = extra.get("id").stringValue();
			var expectedAreaM2 = extra.get("expectedAreaM2").doubleValue();

			assertThat(expectedAreaM2).as("expected area for %s", id).isPositive();

			var actualAreaM2 = GeometryArea.areaM2(feature.getGeometry());
			var relativeError = Math.abs(actualAreaM2 - expectedAreaM2) / expectedAreaM2;

			assertThat(relativeError)
				.as("area for %s: java=%s qgis=%s", id, actualAreaM2, expectedAreaM2)
				.isLessThan(0.01);
		}
	}

	@Test
	void nonPolygonalGeometryHasNoArea() {
		assertThat(GeometryArea.areaM2(null)).isZero();
	}
}
