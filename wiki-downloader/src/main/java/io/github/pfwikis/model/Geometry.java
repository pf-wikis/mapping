package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class Geometry {

    private String type = "Point";
    private List<BigDecimal> coordinates;
}
