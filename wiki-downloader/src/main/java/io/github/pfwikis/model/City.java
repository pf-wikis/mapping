package io.github.pfwikis.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class City {

    @JsonProperty("_pageName")
    private String pageName;
    @JsonProperty("latlong  lon")
    private BigDecimal coordsLon;
    @JsonProperty("latlong  lat")
    private BigDecimal coordsLat;
    private String population;
    private String size;
    private int capital;
}
