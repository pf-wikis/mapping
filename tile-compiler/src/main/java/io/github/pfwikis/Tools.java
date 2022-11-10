package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.beust.jcommander.internal.Lists;

import lombok.extern.java.Log;

@Log
public class Tools {
    public static void run(Object... command) throws IOException {
        run(Arrays.asList(command));
    }

    public static void run(List<?> command) throws IOException {
        var strCommand = command.stream().map(Object::toString).toList();
        log.info(String.join(" ", strCommand));
        var proc = new ProcessBuilder()
            .inheritIO()
            .command(strCommand)
            .start();
        proc.onExit().join();
        if(proc.exitValue() != 0) {
            throw new RuntimeException("Exited with non-zero code");
        }
    }

    public static File tmpDir() throws IOException {
        var dir = Files.createTempDirectory("pathfinder-mapping-").toFile();
        FileUtils.forceDeleteOnExit(dir);
        return dir;
    }

    public static void runAndPipeTo(File target, Object... command) throws IOException {
        var strCommand = Arrays.stream(command).map(Object::toString).toList();
        log.info(String.join(" ", strCommand));
        var proc = new ProcessBuilder()
            .redirectError(Redirect.INHERIT)
            .redirectInput(Redirect.INHERIT)
            .redirectOutput(target)
            .command(strCommand)
            .start();
        proc.onExit().join();
        if(proc.exitValue() != 0) {
            throw new RuntimeException("Exited with non-zero code");
        }
    }





    //some helper to make the code a bit cleaner
    public static void qgis(String qgisCommand, File in, File out, Object... args) throws IOException {
        var command = Lists.<Object>newArrayList(
            "qgis_process", "run", qgisCommand,
            "--distance_units=meters",
            "--area_units=m2",
            "--ellipsoid=EPSG:7030",
            "--INPUT="+in,
            "--OUTPUT="+out
        );
        command.addAll(Arrays.asList(args));
        run(command);
    }

    public static void mapshaper(File in, File out, Object... args) throws IOException {
        var command = Lists.<Object>newArrayList("mapshaper", in);
        command.addAll(Arrays.asList(args));
        command.add("-o");
        command.add(out);
        if(in.equals(out)) {
            command.add("force");
        }
        command.add("geojson-type=FeatureCollection");
        run(command);
    }

    public static String jsDegToMeters(String lat) {
        return "111319.491*"+jsScaleFactor(lat);
    }

    public static String jsScaleFactor(String lat) {
        return "(1 + 0.00001120378*(Math.cos(2*"+lat+"/180*Math.PI) - 1)) / Math.cos("+lat+"/180*Math.PI)";
    }

    public static String jsPixelSizeMinzoomFunction(double minPixelSize, String attribute) {
        return "Math.floor(Math.log2(($threshold)/($attribute)))"
            .replace("$threshold", Double.toString(minPixelSize*111319.491))
            .replace("$attribute", attribute);
    }
}
