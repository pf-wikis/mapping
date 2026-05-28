package io.github.pfwikis.model;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Range;

import io.github.pfwikis.layercompiler.steps.TimeMetaCollect.TimeMeta;
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
    	private Range<Integer> time=Range.all();
    	private TimeMeta timeMeta;
    }
}
