package io.github.pfwikis.layercompiler.steps.model.data;

import java.nio.file.Path;

import com.google.common.io.Files;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.Jackson;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class GeoDataPath extends GeoData {
	
	private final Path path;
	
	@Override @SneakyThrows
	public FeatureCollection toFeatureCollection() {
		return Jackson.JSON.readerFor(FeatureCollection.class).readValue(path.toFile());
	}
	
	@Override
	public Path toTmpFile(StepExecutor step) {
		return path;
	}

	@Override
	@SneakyThrows
	public byte[] toBytes() {
		return Files.toByteArray(path.toFile());
	}

	@Override
	public boolean isEmpty() {
		return false;
	}
}