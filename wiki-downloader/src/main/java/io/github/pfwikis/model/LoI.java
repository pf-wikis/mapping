package io.github.pfwikis.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoI {
    @JsonProperty("_pageName")
    private String pageName;
    @JsonProperty("latlong  lon")
    private BigDecimal coordsLon;
    @JsonProperty("latlong  lat")
    private BigDecimal coordsLat;
    private String type;
    private String name;
}
