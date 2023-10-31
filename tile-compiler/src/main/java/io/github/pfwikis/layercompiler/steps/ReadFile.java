package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.run.Tools;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReadFile extends LCStep {

    private final File file;
    private final String layer;

    @Override
    public byte[] process() throws IOException {
        var finalFile = Path.of("../sources").resolve(file.toPath()).toFile().getCanonicalFile();

        if(finalFile.getName().endsWith(".gpkg")) {
            return Tools.ogr2ogr(ctx.getMappingDataFile().toString(), "-dim", "XY", "-mapFieldType", "DateTime=String", layer);
        }
        return FileUtils.readFileToByteArray(finalFile);
    }

}
