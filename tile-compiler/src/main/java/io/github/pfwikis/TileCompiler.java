package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

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

        var ttmp = new File("./tippecanoe-tmp").getAbsoluteFile().getCanonicalFile();
        ttmp.mkdirs();

        Tools.tippecanoe(
            "-z"+options.getMaxZoom(),
            "-n", "golarion",
            "-o", new File(targetDir, "golarion.pmtiles"),
            "--force",
            "--detect-shared-borders",
            "--preserve-input-order",
            "-B", "0",
            "--coalesce-densest-as-needed",
            "-t", ttmp,
            layers
        );
    }

    private void compileLayers() throws Exception {
        new LayersCompiler(this).compile();
    }

    private void compileSprites() throws IOException {
        log.info("Compiling Sprites");
        Tools.spriteZero(new File(spriteDir, "sprites"), new File("sprites/"));
        Tools.spriteZero(new File(spriteDir, "sprites@2x"), new File("sprites/"), "--retina");
    }
}
