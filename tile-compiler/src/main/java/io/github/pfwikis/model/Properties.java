package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Range;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class Properties extends AnyJson {
	private Long fid;
	private Label label;
	private JsonNode labels;
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
	private List<String> colorStack;
	private String halo;
	private UUID uuid;
	private BigDecimal angle;
	private BigDecimal width;
	private BigDecimal height;
	private Boolean inSubregion;
	private Integer borderType;
	private BigDecimal gc_errorx;
	private BigDecimal gc_errory;
	private Double areaM2;
	private Boolean noSmooth;
	private Pattern pattern;
	@JsonUnwrapped(prefix = "time.")
	private Range<Integer> time=Range.all();
	private Integer timeIndexStart;
	private Integer timeIndexEnd;
	
	public void setTimeStart(Integer timeStart) {
		if(timeStart != null)
			time = time.intersection(Range.atLeast(timeStart));
	}
	
	public void setTimeEnd(Integer timeEnd) {
		if(timeEnd != null)
			time = time.intersection(Range.lessThan(timeEnd));
	}
	
	public static enum Pattern {
		pebbles,
		NONE;
	}
	
	public String simpleLabel() {
		if(label!=null)
			return label.toString();
		if(labels != null && !labels.isNull()) {
			if(labels.isTextual() && !labels.textValue().isBlank()) {
				return labels.textValue();
			}
			return labels.toString();
		}
			
		return null;
	}
}
