package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.github.pfwikis.util.time.TimeRange;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import tools.jackson.databind.JsonNode;

@Setter
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class Properties extends AnyJson {
	private Long fid;
	private Label label;
	private JsonNode labels;
	private String link;
	private String text;
	private String icon;
	private Integer minzoom;
	private Integer maxzoom;
	private Integer articleLength;
	private Integer size;
	private Boolean capital;
	private String type;
	private String color;
	private List<String> colorStack;
	private List<UUID> uuids;
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
	/*used during processing*/
	@JsonUnwrapped
	private TimeRange time=TimeRange.always();
	/*only used for output*/
	private Integer timeIndexStart;
	/*only used for output*/
	private Integer timeIndexEnd;
	private ExportProperties export;
	private Integer pregroupMinzoom;
	
	
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
	
	@Data
	@FieldNameConstants
	public static class ExportProperties {
		private int tileMinzoom;
		private int tileMaxzoom;
	}
}
