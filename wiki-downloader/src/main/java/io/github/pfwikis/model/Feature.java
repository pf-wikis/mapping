package io.github.pfwikis.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@JsonPropertyOrder({"type", "properties", "geometry"})
@NoArgsConstructor
public class Feature {

    private String type = "Feature";
    private Properties properties;
    private Geometry geometry;

    public Feature(Properties properties, Geometry geometry) {
        this.properties = properties;
        this.geometry = geometry;
    }
}
