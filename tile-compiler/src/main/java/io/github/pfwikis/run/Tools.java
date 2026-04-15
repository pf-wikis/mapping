package io.github.pfwikis.run;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class Tools {

	public static LCContent qgis(LCStep step, String qgisCommand, LCContent in, Object... args) throws IOException {
    	return qgis(step, qgisCommand, "OUTPUT", in, args);
    }

	public static LCContent qgis(LCStep step, String qgisCommand, String outputName, LCContent in, Object... args) throws IOException {
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

    public static LCContent mapshaper(LCStep step, LCContent in, Object... args) throws IOException {
        return Runner.run(
        	step,
            "mapshaper", "-i", new Runner.TmpGeojson(in),
            args,
            "-o", new Runner.OutFile(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }
    
    public static LCContent mapshaper0(LCStep step, Object... args) throws IOException {
    	return Runner.run(
    		step,
            "mapshaper",
            args,
            "-o", new Runner.OutFile(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static LCContent mapshaper2(LCStep step, LCContent in1, LCContent in2, Object... args) throws IOException {
        return Runner.run(
        	step,
            "mapshaper", "-i", new Runner.TmpGeojson(in1), new Runner.TmpGeojson(in2),
            args,
            "-o", new Runner.OutFile(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static LCContent ogr2ogr(LCStep step, Object... args) throws IOException {
        return Runner.run(step, "ogr2ogr", new Runner.OutFile(), args);
    }

    public static void spriteZero(LCStep step, Object... args) throws IOException {
		Runner.run(step, "spritezero", args);
	}
    
    public static LCContent tippecanoe(LCStep step, String resultFormat, Object... args) throws IOException {
    	var tmpDir = new File(Runner.TMP_DIR, "tippecanoe-tmp/"+UUID.randomUUID()).getAbsoluteFile().getCanonicalFile();
    	tmpDir.mkdirs();
    	try {
    		return Runner.run(step, "tippecanoe", args, "-o", new Runner.OutFile("", resultFormat), "-t", tmpDir);
    	} finally {
    		FileUtils.deleteQuietly(tmpDir);
    	}
	}
}
