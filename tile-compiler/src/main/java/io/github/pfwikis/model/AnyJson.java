package io.github.pfwikis.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import lombok.Getter;
import lombok.SneakyThrows;

public class AnyJson {
    @Getter(onMethod_=@JsonAnyGetter)
    private Map<String, JsonNode> unknownFields = new HashMap<>();

    @JsonAnySetter
    public void setOtherField(String name, JsonNode value) {
        unknownFields.put(name, value);
    }
    
    private final static ObjectMapper MAPPER = new ObjectMapper();
    @SneakyThrows
    public <T extends AnyJson> T copy() {
		TokenBuffer tb = new TokenBuffer(MAPPER.getFactory().getCodec(), false);
		MAPPER.writeValue(tb, this);
		return (T)MAPPER.readValue(tb.asParser(), this.getClass());
    }
}
