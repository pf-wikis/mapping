package io.github.pfwikis.model;

import java.util.ArrayList;
import java.util.List;
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

@RequiredArgsConstructor
public class Labels {

	private final List<Label> labels;
	
	@JsonCreator
	public static Labels fromJson(JsonNode v) {
		if(v.isNull()) {
			return new Labels(new ArrayList<>());
		}
		if(v.isTextual()) {
			var txt = v.asText();
			try {
				var n = LCContent.MAPPER.readValue(txt, JsonNode.class);
				return fromJson(n);
			} catch(Exception e) {
				return new Labels(Lists.newArrayList(Label.fromJson(v)));
			}
		}
		if(v.isObject()) {
			return new Labels(Lists.newArrayList(Label.fromJson(v)));
		}
		if(v.isArray()) {
			return new Labels(StreamSupport.stream(v.spliterator(), false)
				.map(Label::fromJson)
				.toList());
		}
		throw new IllegalStateException("Can't map label of type "+v.getClass());
	}
	
	@JsonValue
	public Object toJson() {
		if(labels.isEmpty()) {
			return null;
		}
		if(labels.size()==1) {
			return labels.getFirst().toJson();
		}
		return labels;
	}
	
	@Override
	public String toString() {
		try {
			return LCContent.MAPPER.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}
	
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Label {
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
		
		@JsonValue
		public Object toJson() {
			if(id == null) {
				return label;
			}
			return this;
		}
	}
}
