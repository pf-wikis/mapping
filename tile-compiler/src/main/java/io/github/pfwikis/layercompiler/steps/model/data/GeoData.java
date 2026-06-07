package io.github.pfwikis.layercompiler.steps.model.data;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Runner.OutFile;
import io.github.pfwikis.util.Jackson;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class GeoData {
	
	public abstract byte[] toBytes();
	
	public abstract boolean isEmpty();

	public String toRawString() {
		return new String(toBytes(), StandardCharsets.UTF_8);
	}

	@SneakyThrows
	public FeatureCollection toFeatureCollection() {
		return Jackson.JSON.readerFor(FeatureCollection.class).readValue(toBytes());
	}
	
	@SneakyThrows
	public Path toTmpFile(StepExecutor step) {
		try (var _=step.measureSubtime("IO write tmp file")) {
			var tmpFile = Runner.tmpGeojson(step, new OutFile());
	        FileUtils.writeByteArrayToFile(tmpFile, toBytes());
	        return tmpFile.toPath();
		}
	}
	
	/********    factory methods          ***/
	public static GeoData from(Path path) {
		return new GeoDataPath(path);
	}

	public static GeoData from(File file) {
		return from(file.toPath());
	}

	public static GeoData empty() {
		return GeoDataEmpty.INSTANCE;
	}
	
	public static GeoData from(FeatureCollection col) {
		return new GeoDataOM<>(col);
	}
}
