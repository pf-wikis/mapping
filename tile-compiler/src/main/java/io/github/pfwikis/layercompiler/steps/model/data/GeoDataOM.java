package io.github.pfwikis.layercompiler.steps.model.data;

import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.Jackson;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import tools.jackson.databind.util.TokenBuffer;

@RequiredArgsConstructor
public class GeoDataOM<T> extends GeoData {
	private final T val;
	
	@Override
	@SneakyThrows
	public byte[] toBytes() {
		var res = Jackson.JSON.writeValueAsBytes(val);
		return res;
	}

	@Override
	@SneakyThrows
	public FeatureCollection toFeatureCollection() {
		TokenBuffer tb = new TokenBuffer(Jackson.JSON._serializationContext(), false);
		Jackson.JSON.writeValue(tb, val);
		var res = Jackson.JSON.readValue(tb.asParser(), FeatureCollection.class);
		return res;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}
}