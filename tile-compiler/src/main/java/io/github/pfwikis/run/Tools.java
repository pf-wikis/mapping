package io.github.pfwikis.run;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;

public class Tools {

	public static GeoData qgis(StepExecutor step, String qgisCommand, GeoData in, Object... args) throws IOException {
    	return qgis(step, qgisCommand, "OUTPUT", in, args);
    }

	public static GeoData qgis(StepExecutor step, String qgisCommand, String outputName, GeoData in, Object... args) throws IOException {
        return Runner.run(
        	step,
            "qgis_process",
            qgisCommand.startsWith("native:")?"--no-python":List.of(),
            "run", qgisCommand,
            "--distance_units=meters",
            "--area_units=m2",
            "--ellipsoid=EPSG:4326",
            new Runner.TmpGeojson("--INPUT=", in),
            new Runner.OutFile("--"+outputName+"="),
            args
        );
    }

    public static GeoData mapshaper(StepExecutor step, GeoData in, Object... args) throws IOException {
        return Runner.run(
        	step,
            "mapshaper", "-i", new Runner.TmpGeojson(in),
            args,
            "-o", new Runner.OutFile(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }
    
    public static GeoData mapshaper0(StepExecutor step, Object... args) throws IOException {
    	return Runner.run(
    		step,
            "mapshaper",
            args,
            "-o", new Runner.OutFile(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static GeoData mapshaper2(StepExecutor step, GeoData in1, GeoData in2, Object... args) throws IOException {
        return Runner.run(
        	step,
            "mapshaper", "-i", new Runner.TmpGeojson(in1), new Runner.TmpGeojson(in2),
            args,
            "-o", new Runner.OutFile(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static GeoData ogr2ogr(StepExecutor step, Object... args) throws IOException {
        return Runner.run(step, "ogr2ogr", new Runner.OutFile(), args);
    }

    public static void spriteZero(StepExecutor step, Object... args) throws IOException {
		Runner.run(step, "spritezero", args);
	}
    
    public static GeoData tippecanoe(StepExecutor step, String resultFormat, Object... args) throws IOException {
    	var tmpDir = new File(Runner.TMP_DIR, "tippecanoe-tmp/"+UUID.randomUUID()).getAbsoluteFile().getCanonicalFile();
    	tmpDir.mkdirs();
    	try {
    		return Runner.run(step, "tippecanoe", args, "-o", new Runner.OutFile("", resultFormat), "-t", tmpDir);
    	} finally {
    		FileUtils.deleteQuietly(tmpDir);
    	}
	}
}
