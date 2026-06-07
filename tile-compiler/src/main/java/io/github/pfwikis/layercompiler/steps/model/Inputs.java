package io.github.pfwikis.layercompiler.steps.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.util.time.TimeRange;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Inputs {
	private final Time.DataState timeState;
	private final TimeRange time;
	private final SequencedMap<String, GeoData> inputs = new LinkedHashMap<>();
	
	public static Inputs from(Time.DataState timeState, String key, TimeSlice slice) {
		var res = new Inputs(timeState, slice.getTime());
		res.inputs.put(key, slice.getData());
		return res;
	}
	
	public static Inputs from(Time.DataState timeState, String key, TimeRange time, GeoData content) {
		var res = new Inputs(timeState, time);
		res.inputs.put(key, content);
		return res;
	}
	
	public static Inputs from(Time.DataState timeState, TimeRange time, Map<String, GeoData> inputs) {
		var res = new Inputs(timeState, time);
		res.inputs.putAll(inputs);
		return res;
	}
	
	public static Inputs from(Time.DataState timeState, TimeRange time) {
		return new Inputs(timeState, time);
	}
	
	public GeoData getInput() {
        return getInput("in");
    }
	
	public GeoData getInput(String key) {
        return inputs.get(key);
    }
	
	@Override
	public String toString() {
		return time.toString();
	}

	
}