package io.github.pfwikis.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@JsonPropertyOrder({"type", "properties", "tippecanoe", "geometry"})
@NoArgsConstructor
public class Feature {

    private String type = "Feature";
    private Properties properties;
    private Tippecanoe tippecanoe = new Tippecanoe();
    private Geometry geometry;

    public Feature(Properties properties, Geometry geometry) {
        this.properties = properties;
        this.geometry = geometry;
    }

    @Setter
    @Getter
    public static class Tippecanoe {
        private Integer minzoom;
    }
}
