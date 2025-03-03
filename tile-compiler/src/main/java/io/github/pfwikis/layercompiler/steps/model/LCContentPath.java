package io.github.pfwikis.layercompiler.steps.model;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

import lombok.SneakyThrows;

public class LCContentPath extends LCContent {
	
	private final Path path;
	
	public LCContentPath(Path path, boolean temporaryFile) {
		this.path = path;
		if(temporaryFile)
			this.temporaryFilesToDelete.add(path);
	}

	@Override @SneakyThrows
	public InputStream toInputStream() {
		checkValidUsage();
		return new BufferedInputStream(new FileInputStream(path.toFile()));
	}
	
	@Override @SneakyThrows
	public <T> T toParsed(Class<T> cl) {
		checkValidUsage();
		try(var in=toInputStream()) {
			return MAPPER.readerFor(cl).readValue(path.toFile());
		}
	}
	
	@Override
	public Path toTmpFile(LCStep step) {
		return path;
	}
}