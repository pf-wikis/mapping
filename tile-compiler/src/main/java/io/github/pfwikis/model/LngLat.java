package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record LngLat(double lng, double lat) {

    @JsonCreator
    public static LngLat of(double[] raw) {
        return new LngLat(raw[0], raw[1]);
    }

    @JsonValue
    public double[] toRaw() {
        return new double[]{lng, lat};
    }

    public LngLat norm() {
        double nlng = lng;
        while(nlng < -180) nlng+=360;
        while(nlng > 180) nlng-=360;
        return new LngLat(nlng, lat);
    }

	public LngLat middle(LngLat o) {
		return new LngLat((lng+o.lng)/2d, (lat+o.lat)/2d);
	}

	public LngLat roundTo(int prec) {
		return new LngLat(
				new BigDecimal(lng).setScale(prec, RoundingMode.HALF_UP).doubleValue(),
				new BigDecimal(lat).setScale(prec, RoundingMode.HALF_UP).doubleValue()
		);
	}
}
