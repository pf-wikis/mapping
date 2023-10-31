package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.run.Tools;

public class ReadFile extends LCStep {

    private final File file;
    private final String layer;

    
    public ReadFile(File file, String layer) {
		this.file = file;
		this.layer = layer;
		
		if(file == null) {
			Objects.requireNonNull(layer, "Either file or layer must not be null for READ_FILE.");
		}
	}


	@Override
    public byte[] process() throws IOException {
		if(file != null) {
			var finalFile = Path.of("../sources").resolve(file.toPath()).toFile().getCanonicalFile();
			return FileUtils.readFileToByteArray(finalFile);
		}
		else {
            return Tools.ogr2ogr(ctx.getMappingDataFile().toString(), "-dim", "XY", "-mapFieldType", "DateTime=String", layer);
        }
    }

}
