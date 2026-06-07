package io.github.pfwikis.layercompiler.steps.model.data;

public class GeoDataEmpty extends GeoData {
	public static GeoData INSTANCE = new GeoDataEmpty();
	
	@Override
	public byte[] toBytes() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEmpty() {
		return true;
	}
}