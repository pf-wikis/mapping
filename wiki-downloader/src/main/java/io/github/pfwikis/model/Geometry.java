package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonPropertyOrder({"type", "coordinates"})
public class Geometry {

    public static final MathContext ROUND_TO_7 = new MathContext(7, RoundingMode.HALF_UP);

    private String type = "Point";
    private List<BigDecimal> coordinates;

    public Geometry(City city) {
        coordinates = List.of(
                city.getCoordsLon().round(ROUND_TO_7),
                city.getCoordsLat().round(ROUND_TO_7)
        );
    }
}
