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

	public Geometry(BigDecimal lon, BigDecimal lat) {
		//wrap around for better viewing in qgis
    	if(lon.compareTo(BigDecimal.valueOf(-138))<0)
    		lon=lon.add(BigDecimal.valueOf(360));
		
		coordinates = List.of(
            lon.round(ROUND_TO_7),
            lat.round(ROUND_TO_7)
        );
	}
}
