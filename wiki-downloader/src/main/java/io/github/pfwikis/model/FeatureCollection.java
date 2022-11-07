package io.github.pfwikis.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"name", "type", "comment", "features"})
@NoArgsConstructor
public class FeatureCollection {

    private String type = "FeatureCollection";
    private String name;
    private String comment = "Do not edit this file! It will be automatically updated and all manual changes are lost.";
    private List<Feature> features;

    public FeatureCollection(String name, List<Feature> features) {
        this.name = name;
        this.features = features;
    }
}
