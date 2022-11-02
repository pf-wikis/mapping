package io.github.pfwikis.model;

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
}
