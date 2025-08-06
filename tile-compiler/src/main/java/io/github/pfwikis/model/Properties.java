package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class Properties extends AnyJson {
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
