package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import io.github.pfwikis.layercompiler.LayerCompiler;
import io.github.pfwikis.layercompiler.steps.LCStep.Ctx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MapsCompiler {

    private final TileCompiler tileCompiler;
    private final File sources = new File("../sources");

    public void compile() throws IOException {
        var tasks = Files.walk(sources.toPath())
            .map(Path::toFile)
            .filter(f->f.getName().endsWith(".geojson") || f.getName().endsWith(".gpkg"))
            .map(f->new LayerCompiler(new Ctx(
                FilenameUtils.removeExtension(f.getName()),
                tileCompiler.getOptions(),
                tileCompiler.getGeo(),
                f)))
            .peek(LayerCompiler::init)
            .collect(Collectors.toMap(lc->lc.getCtx().getName(), Function.identity()));

        //cross reference dependencies
        tasks.get("borders").getDependencies().add(tasks.get("continents"));


        tasks.values()
            .parallelStream()
            .forEach(LayerCompiler::compile);
    }
}
