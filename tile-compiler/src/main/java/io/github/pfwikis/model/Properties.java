package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class Properties extends AnyJson {
	private Labels label;
	private String link;
	private String text;
	private String icon;
	private Integer filterMinzoom;
	private Integer filterMaxzoom;
	private Integer articleLength;
	private Integer size;
	private Boolean capital;
	private String type;
	private String color;
	private String halo;
	private UUID uuid;
	private BigDecimal angle;
	private BigDecimal width;
	private BigDecimal height;
	private Boolean inSubregion;
	
	public String simpleLabel() {
		if(label!=null)
			return label.toString();
		return null;
	}
}
