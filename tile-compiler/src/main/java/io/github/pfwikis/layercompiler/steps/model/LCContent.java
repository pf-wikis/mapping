package io.github.pfwikis.layercompiler.steps.model;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Runner;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class LCContent {
	
	public static final ObjectMapper MAPPER = new ObjectMapper()
		.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)
		.setDefaultPropertyInclusion(Include.NON_NULL);

	@Setter
	private int numberOfValidUses = 1;
	private int numberOfUses;
	@Setter
	protected String name;
	protected List<Path> temporaryFilesToDelete = new ArrayList<>();
	
	public synchronized void finishUsage() {
		numberOfUses++;
		if(numberOfUses >= numberOfValidUses) {
			temporaryFilesToDelete.forEach(f->FileUtils.deleteQuietly(f.toFile()));
			cleanup();
		}
	}
	
	protected synchronized void checkValidUsage() {
		if(numberOfUses >= numberOfValidUses) {
			throw new IllegalStateException("Content "+name+" was used "+(numberOfUses+1)+" times even though it is only allows to be used "+numberOfValidUses);
		}
	}

	protected void cleanup() {}

	public abstract InputStream toInputStream();

	@SneakyThrows
	public byte[] toBytes() {
		checkValidUsage();
		try(var in=toInputStream()) {
			return IOUtils.toByteArray(toInputStream());
		}
	}

	public String toRawString() {
		checkValidUsage();
		return new String(toBytes(), StandardCharsets.UTF_8);
	}

	public JsonNode toJSONNode() {
		checkValidUsage();
		return toParsed(JsonNode.class);
	}
	
	@SneakyThrows
	public <T> T toParsed(Class<T> cl) {
		checkValidUsage();
		try(var in=toInputStream()) {
			return MAPPER.readerFor(cl).readValue(toInputStream());
		}
	}

	@SneakyThrows
	public FeatureCollection toFeatureCollection() {
		return toParsed(FeatureCollection.class);
	}
	
	public FeatureCollection toFeatureCollectionAndFinish() {
		var res = toFeatureCollection();
		this.finishUsage();
		return res;
	}

	@SneakyThrows
	public mil.nga.sf.geojson.FeatureCollection toNgaFeatureCollection() {
		return toParsed(mil.nga.sf.geojson.FeatureCollection.class);
	}
	
	@SneakyThrows
	public Path toTmpFile(LCStep step) {
		checkValidUsage();
		var tmpFile = Runner.tmpGeojson(step);
		temporaryFilesToDelete.add(tmpFile.toPath());
        tmpFile.deleteOnExit();
        FileUtils.writeByteArrayToFile(tmpFile, toBytes());
        return tmpFile.toPath();
	}
	
	
	
	
	
	
	
	/********    factory methods          ***/

	public static LCContent from(Path path, boolean temporary) {
		return new LCContentPath(path, temporary);
	}

	public static LCContent from(File file, boolean temporary) {
		return from(file.toPath(), temporary);
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
	
	@SneakyThrows
	public static LCContent from(mil.nga.sf.geojson.FeatureCollection v) {
		return new LCContentOM<>(mil.nga.sf.geojson.FeatureCollection.class, v);
	}
	
	public static LCContent from(JsonNode n) {
		return new LCContentOM<>(JsonNode.class, n);
	}
}
