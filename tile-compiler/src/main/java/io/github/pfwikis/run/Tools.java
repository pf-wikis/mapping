package io.github.pfwikis.run;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.LCContent;

public class Tools {

    //some helper to make the code a bit cleaner
    public static LCContent qgis(String qgisCommand, LCContent in, Object... args) throws IOException {
        return Runner.run(
            "qgis_process", "run", qgisCommand,
            "--distance_units=meters",
            "--area_units=m2",
            "--ellipsoid=EPSG:4326",
            new Runner.TmpGeojson("--INPUT=", in),
            new Runner.OutGeojson("--OUTPUT="),
            args
        );
    }

    public static LCContent mapshaper(LCContent in, Object... args) throws IOException {
        return Runner.runPipeOut(
            "mapshaper", "-i", new Runner.TmpGeojson(in),
            args,
            "-o", "-", "format=geojson", "geojson-type=FeatureCollection",
            "precision=0.00000001"
        );
    }

    public static LCContent mapshaper2(LCContent in1, LCContent in2, Object... args) throws IOException {
        return Runner.runPipeOut(
            "mapshaper", "-i", new Runner.TmpGeojson(in1), new Runner.TmpGeojson(in2),
            args,
            "-o", "-", "format=geojson", "geojson-type=FeatureCollection"
        );
    }

    public static LCContent geojsonPolygonLabels(LCContent in, String... args) throws IOException {
        return Runner.runPipeOut("geojson-polygon-labels", args, new Runner.TmpGeojson(in));
    }

    public static LCContent ogr2ogr(Object... args) throws IOException {
        return Runner.run("ogr2ogr", new Runner.OutGeojson(), args);
    }

    public static void spriteZero(Object... args) throws IOException {
		Runner.run("spritezero", args);
	}
    
    public static void tippecanoe(Object... args) throws IOException {
		Runner.run("tippecanoe", args);
	}
}
