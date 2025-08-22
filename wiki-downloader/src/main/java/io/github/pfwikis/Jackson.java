package io.github.pfwikis;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Jackson {

    public static ObjectMapper get() {
        return new ObjectMapper()
        		.setSerializationInclusion(Include.NON_NULL)
        		.registerModule(new JavaTimeModule());
    }
}
