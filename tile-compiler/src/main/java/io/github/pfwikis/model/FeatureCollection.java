package io.github.pfwikis.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.github.pfwikis.layercompiler.steps.time.TimeMetaCollect.TimeMeta;
import io.github.pfwikis.util.Jackson;
import io.github.pfwikis.util.time.TimeRange;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonDeserialize;

@Getter
@Setter
public class FeatureCollection extends AnyJson {
	private String type = "FeatureCollection";
    private List<Feature> features = new ArrayList<>();
    private FCProperties properties = new FCProperties();
    
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class FCProperties {
    	@JsonUnwrapped
    	private TimeRange time=TimeRange.always();
    	private TimeMeta timeMeta;
    }
}
