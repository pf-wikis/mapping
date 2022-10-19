package io.github.pfwikis.model;

import java.math.BigDecimal;

import com.google.gson.annotations.SerializedName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class City {

    @SerializedName("_pageName")
    private String pageName;
    @SerializedName("latlong  lon")
    private BigDecimal coordsLon;
    @SerializedName("latlong  lat")
    private BigDecimal coordsLat;
    private String population;
}
