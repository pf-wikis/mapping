package io.github.pfwikis.run;

import java.io.IOException;

public class Tools {

    //some helper to make the code a bit cleaner
    public static byte[] qgis(String qgisCommand, byte[] in, Object... args) throws IOException {
        return Runner.run(
            "qgis_process", "run", qgisCommand,
            "--distance_units=meters",
            "--area_units=m2",
            "--ellipsoid=EPSG:7030",
            new Runner.TmpGeojson("--INPUT=", in),
            new Runner.OutGeojson("--OUTPUT="),
            args
        );
    }

    public static byte[] mapshaper(byte[] in, Object... args) throws IOException {
        return Runner.runPipeInOut(in,
            "mapshaper", "-",
            args,
            "-o", "-", "format=geojson", "geojson-type=FeatureCollection"
        );
    }

    public static byte[] mapshaper2(byte[] in1, byte[] in2, Object... args) throws IOException {
        return Runner.runPipeOut(
            "mapshaper", "-i", new Runner.TmpGeojson(in1), new Runner.TmpGeojson(in2),
            args,
            "-o", "-", "format=geojson", "geojson-type=FeatureCollection"
        );
    }

    public static byte[] jq(byte[] in, String... jqCommands) throws IOException {
        return Runner.runPipeInOut(in, "jq", jqCommands);
    }

    public static byte[] geojsonPolygonLabels(byte[] in, String... args) throws IOException {
        return Runner.runPipeOut("geojson-polygon-labels", args, new Runner.TmpGeojson(in));
    }

    public static byte[] ogr2ogr(String... args) throws IOException {
        return Runner.run("ogr2ogr", new Runner.OutGeojson(), args);
    }
}
