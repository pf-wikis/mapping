package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.Tools;

public class ConvertToGeoJson extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        if(f.getName().endsWith(".geojson")) {
            return f;
        }
        else if(f.getName().endsWith(".gpkg")) {
            var tmp = tmpGeojson();
            Tools.run("ogr2ogr", tmp, f, "-dim", "XY", "-mapFieldType", "DateTime=String");
            return tmp;
        }
        else {
            throw new UnsupportedOperationException();
        }
    }
}

