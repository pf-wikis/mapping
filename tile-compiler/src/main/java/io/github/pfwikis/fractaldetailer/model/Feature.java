package io.github.pfwikis.fractaldetailer.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Feature extends AnyJson {

    private Geometry geometry;
}
