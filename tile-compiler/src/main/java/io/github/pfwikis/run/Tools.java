package io.github.pfwikis.run;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class Tools {

    //some helper to make the code a bit cleaner
    public static LCContent qgis(LCStep step, String qgisCommand, LCContent in, Object... args) throws IOException {
        return Runner.run(
        	step,
            "qgis_process", "run", qgisCommand,
            "--distance_units=meters",
            "--area_units=m2",
            "--ellipsoid=EPSG:4326",
            new Runner.TmpGeojson("--INPUT=", in),
            new Runner.OutGeojson("--OUTPUT="),
            args
        );
    }

    public static LCContent mapshaper(LCStep step, LCContent in, Object... args) throws IOException {
        return Runner.run(
        	step,
            "mapshaper", "-i", new Runner.TmpGeojson(in),
            args,
            "-o", new Runner.OutGeojson(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }
    
    public static LCContent mapshaper0(LCStep step, Object... args) throws IOException {
    	return Runner.run(
    		step,
            "mapshaper",
            args,
            "-o", new Runner.OutGeojson(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static LCContent mapshaper2(LCStep step, LCContent in1, LCContent in2, Object... args) throws IOException {
        return Runner.run(
        	step,
            "mapshaper", "-i", new Runner.TmpGeojson(in1), new Runner.TmpGeojson(in2),
            args,
            "-o", new Runner.OutGeojson(), "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static LCContent geojsonPolygonLabels(LCStep step, LCContent in, String... args) throws IOException {
        return Runner.runPipeOut(step, "geojson-polygon-labels", args, new Runner.TmpGeojson(in));
    }

    public static LCContent ogr2ogr(LCStep step, Object... args) throws IOException {
        return Runner.run(step, "ogr2ogr", new Runner.OutGeojson(), args);
    }

    public static void spriteZero(LCStep step, Object... args) throws IOException {
		Runner.run(step, "spritezero", args);
	}
    
    public static void tippecanoe(LCStep step, Object... args) throws IOException {
		Runner.run(step, "tippecanoe", args);
	}
}
