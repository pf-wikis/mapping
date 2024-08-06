package io.github.pfwikis.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureCollection extends AnyJson {
	private String type = "FeatureCollection";
    private List<Feature> features = new ArrayList<>();
}
