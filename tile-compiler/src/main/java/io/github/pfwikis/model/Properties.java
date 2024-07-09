package io.github.pfwikis.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Properties extends AnyJson {
	@JsonProperty("Name")
	private String name;
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
}
