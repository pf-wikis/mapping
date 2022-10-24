package io.github.pfwikis.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Feature extends AnyJson {

    private Geometry geometry;
}
