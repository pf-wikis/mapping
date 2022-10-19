package io.github.pfwikis.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Feature {

    private String type = "Feature";
    private Properties properties;
    private Tippecanoe tippecanoe = new Tippecanoe();
    private Geometry geometry;

    @Setter
    @Getter
    public static class Tippecanoe {
        private Integer minzoom;
    }
}
