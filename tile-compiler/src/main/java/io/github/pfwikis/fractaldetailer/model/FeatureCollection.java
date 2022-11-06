package io.github.pfwikis.fractaldetailer.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureCollection extends AnyJson {
    private List<Feature> features;
}
