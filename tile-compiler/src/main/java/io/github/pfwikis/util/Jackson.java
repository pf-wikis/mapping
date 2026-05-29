package io.github.pfwikis.util;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

public class Jackson {
	public static final JsonMapper JSON = JsonMapper.builder()
		.enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
		//.registerModule(new GuavaModule())
		.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
		.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
		.build();
}
