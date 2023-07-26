package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.run.Runner;
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
        targetDir = new File(targetRoot, options.getDataPath());
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
        Files.writeString(new File(targetRoot, "../.env.local").toPath(), "VITE_MAX_ZOOM="+options.getMaxZoom()+"\nVITE_DATA_PATH="+options.getDataPath());
    }

    private void makeTiles() throws IOException {
        var layers = Arrays.stream(geo.listFiles())
            .filter(f->f.getName().endsWith(".geojson"))
            .flatMap(f->List.of("-L", f.getName().substring(0, f.getName().length()-8)+":"+f.toString()).stream())
            .toList();

        Runner.run("tippecanoe",
            "-z"+options.getMaxZoom(),
            "--no-tile-compression",
            "-n", "golarion",
            "-e", new File(targetDir, "golarion"),
            "--force",
            "--detect-shared-borders",
            "--preserve-input-order",
            "-B", "0",
            "--coalesce-densest-as-needed",
            "--maximum-tile-bytes=153600",
            layers
        );

        var time = System.nanoTime();
        //rename extensions for github pages gzipping
        log.info("Renaming .pbf files to .pbf.json");
        Files.walk(new File(targetDir, "golarion").toPath())
            .map(Path::toFile)
            .filter(f->f.getName().endsWith(".pbf"))
            .toList()
            .parallelStream()
            .forEach(f->{
                try {
                    FileUtils.moveFile(f, new File(f.getParentFile(), f.getName()+".json"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        log.info("Renaming took "+TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-time)+"s");

    }

    private void compileLayers() throws Exception {
        new LayersCompiler(this).compile();
    }

    private void compileSprites() throws IOException {
        log.info("Compiling Sprites");
        Runner.run("spritezero", new File(spriteDir, "sprites"), "sprites/");
        Runner.run("spritezero", new File(spriteDir, "sprites@2x"), "sprites/", "--retina");
    }
}
