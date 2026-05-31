package io.github.pfwikis;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class Jackson {

    public static JsonMapper get() {
        return JsonMapper.builder()
    		.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
    		.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
    		.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    		.build();
    }
}
