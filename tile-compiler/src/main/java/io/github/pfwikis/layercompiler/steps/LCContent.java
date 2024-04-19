package io.github.pfwikis.layercompiler.steps;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Runner;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

public interface LCContent {
	
	static final ObjectMapper MAPPER = new ObjectMapper();

	public InputStream toInputStream();

	@SneakyThrows
	public default byte[] toBytes() {
		try(var in=toInputStream()) {
			return IOUtils.toByteArray(toInputStream());
		}
	}

	public default String toJSONString() {
		return new String(toBytes(), StandardCharsets.UTF_8);
	}

	public default JsonNode toJSONNode() {
		return toParsed(JsonNode.class);
	}

	@SneakyThrows
	public default <T> T toParsed(Class<T> cl) {
		try(var in=toInputStream()) {
			return MAPPER.readerFor(cl).readValue(toInputStream());
		}
	}

	@SneakyThrows
	public default FeatureCollection toFeatureCollection() {
		return toParsed(FeatureCollection.class);
	}

	@SneakyThrows
	public default mil.nga.sf.geojson.FeatureCollection toNgaFeatureCollection() {
		return toParsed(mil.nga.sf.geojson.FeatureCollection.class);
	}
	
	@SneakyThrows
	public default Path toTmpFile() {
		var tmpFile = Runner.tmpGeojson();
        tmpFile.deleteOnExit();
        FileUtils.writeByteArrayToFile(tmpFile, toBytes());
        return tmpFile.toPath();
	}

	public static LCContent from(Path path) {
		return new LCContentPath(path);
	}

	public static LCContent from(File file) {
		return from(file.toPath());
	}

	@RequiredArgsConstructor
	public static class LCContentPath implements LCContent {
		private final Path path;

		@Override @SneakyThrows
		public InputStream toInputStream() {
			return new BufferedInputStream(new FileInputStream(path.toFile()));
		}
		
		@Override @SneakyThrows
		public <T> T toParsed(Class<T> cl) {
			try(var in=toInputStream()) {
				return MAPPER.readerFor(cl).readValue(path.toFile());
			}
		}
		
		@Override
		public Path toTmpFile() {
			return path;
		}
	}

	public static LCContent empty() {
		return LCContentEmpty.INSTANCE;
	}
	
	@RequiredArgsConstructor
	public static enum LCContentEmpty implements LCContent {
		INSTANCE;
		
		@Override
		public InputStream toInputStream() {
			throw new IllegalStateException("Called a transformation on an empty content");
		}
	}

	public static LCContent from(byte[] bytes) {
		return new LCContentBytes(bytes);
	}
	
	@RequiredArgsConstructor
	public static class LCContentBytes implements LCContent {
		private final byte[] bytes;

		@Override
		public InputStream toInputStream() {
			return new ByteArrayInputStream(bytes);
		}
		
		@Override
		public byte[] toBytes() {
			return bytes;
		}
	}
	
	public static LCContent from(FeatureCollection col) {
		return new LCContentOM<>(FeatureCollection.class, col);
	}
	
	@RequiredArgsConstructor
	public static class LCContentOM<T> implements LCContent {
		private final Class<T> type;
		private final T val;

		@Override @SneakyThrows
		public byte[] toBytes() {
			return MAPPER.writeValueAsBytes(val);
		}
		
		@Override
		public InputStream toInputStream() {
			return new ByteArrayInputStream(toBytes());
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <R> R toParsed(Class<R> cl) {
			if(cl.equals(type))
				return (R)val;
			return LCContent.super.toParsed(cl);
		}
	}

	@SneakyThrows
	public static LCContent from(mil.nga.sf.geojson.FeatureCollection v) {
		return new LCContentOM<>(mil.nga.sf.geojson.FeatureCollection.class, v);
	}
	
	public static LCContent from(JsonNode n) {
		return new LCContentOM<>(JsonNode.class, n);
	}
}
