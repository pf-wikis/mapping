package io.github.pfwikis.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;

public class Jackson {
	public static final ObjectMapper JSON = new ObjectMapper()
			.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)
			.registerModule(new GuavaModule())
			.setDefaultPropertyInclusion(Include.NON_NULL);
}
