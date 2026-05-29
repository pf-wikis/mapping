package io.github.pfwikis.layercompiler.steps.model;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import tools.jackson.databind.JsonNode;

import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Runner.OutFile;
import io.github.pfwikis.util.Jackson;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class LCContent {
	
	@Setter
	protected String name;
	
	protected void cleanup() {}
	
	public abstract InputStream toInputStream();

	@SneakyThrows
	public byte[] toBytes() {
		try(var in=toInputStream()) {
			return IOUtils.toByteArray(toInputStream());
		}
	}

	public String toRawString() {
		return new String(toBytes(), StandardCharsets.UTF_8);
	}

	public JsonNode toJSONNode() {
		return toParsed(JsonNode.class);
	}
	
	@SneakyThrows
	public <T> T toParsed(Class<T> cl) {
		try(var in=toInputStream()) {
			return Jackson.JSON.readerFor(cl).readValue(toInputStream());
		}
	}

	@SneakyThrows
	public FeatureCollection toFeatureCollection() {
		return toParsed(FeatureCollection.class);
	}
	
	@SneakyThrows
	public Path toTmpFile(LCStepAbstract step) {
		var tmpFile = Runner.tmpGeojson(step, new OutFile());
        FileUtils.writeByteArrayToFile(tmpFile, toBytes());
        return tmpFile.toPath();
	}
	
	
	
	/********    factory methods          ***/
	public static LCContent from(Path path) {
		return new LCContentPath(path);
	}

	public static LCContent from(File file) {
		return from(file.toPath());
	}

	public static LCContent empty() {
		return LCContentEmpty.INSTANCE;
	}
	
	public static LCContent from(byte[] bytes) {
		return new LCContentBytes(bytes);
	}
	
	public static LCContent from(FeatureCollection col) {
		return new LCContentOM<>(FeatureCollection.class, col);
	}
	
	public static LCContent from(JsonNode n) {
		return new LCContentOM<>(JsonNode.class, n);
	}
}
