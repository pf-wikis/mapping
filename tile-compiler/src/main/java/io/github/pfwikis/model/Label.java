package io.github.pfwikis.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import io.github.pfwikis.util.Jackson;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Label {
	private String label;
	private String id;
	
	@JsonCreator
	public static Label fromString(String v) {
		return new Label(v, null);
	}
	
	public static Label fromJson(JsonNode n) {
		try {
			return Jackson.JSON.treeToValue(n, Label.class);
		} catch (JacksonException | IllegalArgumentException e) {
			throw new IllegalStateException();
		}
	}
	
	@Override
	public String toString() {
		return toJson().toString();
	}
	
	@JsonValue
	public Object toJson() {
		if(id == null) {
			return label;
		}
		return Map.of("label", label, "id", id);
	}

	public String identifier() {
		if(id != null) {
			return id;
		}
		return label;
	}
}
