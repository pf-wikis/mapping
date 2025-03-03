package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.ToolVariant;
import io.github.pfwikis.run.Tools;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TileCompiler {

    private CLIOptions options;
    private File geo;
    private File targetDir;
    private File targetRoot;
    private File spriteDir;


    public void run(CLIOptions options) throws Exception {
        this.options = options;
        targetRoot = new File(options.isUseBuildShortcut()
            ?"../frontend/dist"
            : "../frontend/public");
        targetDir = new File(targetRoot, ".");
        spriteDir = new File(targetRoot, "sprites");
        geo = new File("geo");

        //delete old files before we start
        Arrays.stream(targetRoot.listFiles())
            .filter(f->f.getName().startsWith("data"))
            .forEach(FileUtils::deleteQuietly);
        FileUtils.deleteQuietly(geo);

        //create target folders
        geo.mkdirs();
        targetDir.mkdirs();
        spriteDir.mkdirs();


        compileLayers();
        makeTiles();
        compileSprites();

        //give maxZoom to vite
        log.info("Writing .env.local");
        Files.writeString(new File(targetRoot, "../.env.local").toPath(), "VITE_DATA_HASH="+options.getDataHash());
    }

    private void makeTiles() throws IOException {
        var layers = Arrays.stream(geo.listFiles())
            .filter(f->f.getName().endsWith(".geojson"))
            .flatMap(f->List.of("-L", f.getName().substring(0, f.getName().length()-8)+":"+ToolVariant.getTippecanoe().translateFile(f.toPath().toAbsolutePath())).stream())
            .toList();

        var ttmp = new File(Runner.TMP_DIR, "tippecanoe-tmp").getAbsoluteFile().getCanonicalFile();
        ttmp.mkdirs();
        var tmpPMTiles = new File(ttmp, "golarion.pmtiles");

        Tools.tippecanoe(
        	null,
            "-z"+options.getMaxZoom(),
            "--full-detail="+Math.max(14,32-options.getMaxZoom()), //increase detail level on max-zoom
            // |
            // V does not work yet
            //"--generate-variable-depth-tile-pyramid", //does not add levels if the detail is already maxed
            "-n", "golarion",
            "-o", tmpPMTiles,
            "--force",
            "--detect-shared-borders",
            "--preserve-input-order",
            "-B", "0",
            "--coalesce-densest-as-needed",
            "-t", ttmp,
            layers
        );
        var finalOutput = new File(targetDir, "golarion.pmtiles");
        FileUtils.deleteQuietly(finalOutput);
        FileUtils.moveFile(tmpPMTiles, finalOutput);
    }

    private void compileLayers() throws Exception {
        new LayersCompiler(
    		this,
    		Math.min(4, Runtime.getRuntime().availableProcessors())
		).compile();
    }

    private void compileSprites() throws IOException {
        log.info("Compiling Sprites");
        Tools.spriteZero(null, new File(spriteDir, "sprites"), new File("sprites/"));
        Tools.spriteZero(null, new File(spriteDir, "sprites@2x"), new File("sprites/"), "--retina");
    }
}
