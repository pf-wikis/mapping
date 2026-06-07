package io.github.pfwikis.model;

import java.util.ArrayList;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.time.TimeMetaCollect.TimeMeta;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

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
    	private TimeMeta timeMeta;
    }
}
