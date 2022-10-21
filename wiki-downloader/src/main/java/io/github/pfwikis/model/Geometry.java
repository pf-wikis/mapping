package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

@Data
@JsonPropertyOrder({"type", "coordinates"})
public class Geometry {

    private String type = "Point";
    private List<BigDecimal> coordinates;
}
