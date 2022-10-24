package io.github.pfwikis.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"name", "type", "comment", "crs", "features"})
public class FeatureCollection {

    private String type = "FeatureCollection";
    private String name;
    private String comment = "Do not edit this file! It will be automatically updated and all manual changes are lost.";
    private Map<String, Object> crs = Map.of("type", "name", "properties", Map.of("name", "urn:ogc:def:crs:OGC:1.3:CRS84"));
    private List<Feature> features;
}