package io.github.pfwikis.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.beust.jcommander.internal.Lists;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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
			return LCContent.MAPPER.treeToValue(n, Label.class);
		} catch (JsonProcessingException | IllegalArgumentException e) {
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
