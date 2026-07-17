package io.github.pfwikis.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import io.github.pfwikis.util.Jackson;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Label {
	private String label;
	private String id;
	
	@JsonCreator
	public static Label fromString(String v) {
		return new Label(v, null);
	}
	
	@JsonCreator
	public static Label fromJson(ObjectNode n) {
		try {
			var h = Jackson.JSON.treeToValue(n, Helper.class);
			return new Label(h.label, h.id);
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
		return new Helper(label, id);
	}

	public String identifier() {
		if(id != null) {
			return id;
		}
		return label;
	}
	
	private record Helper(String label, String id) {}
}
