package io.github.pfwikis.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import io.github.pfwikis.util.Jackson;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;

@EqualsAndHashCode
public class AnyJson {
    @Getter(onMethod_=@JsonAnyGetter)
    private Map<String, JsonNode> unknownFields = new HashMap<>();

    @JsonAnySetter
    public void setOtherField(String name, JsonNode value) {
        unknownFields.put(name, value);
    }
    
    @SneakyThrows
    public <T extends AnyJson> T copy() {
		TokenBuffer tb = new TokenBuffer(Jackson.JSON.getFactory().getCodec(), false);
		Jackson.JSON.writeValue(tb, this);
		return (T)Jackson.JSON.readValue(tb.asParser(), this.getClass());
    }
}
