package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

import io.github.pfwikis.Tools;
import io.github.pfwikis.layercompiler.LayerCompiler;
import io.github.pfwikis.layercompiler.steps.LCStep.Ctx;
import lombok.extern.java.Log;

@Log
public class GenerateLabelCenters extends LCStep {

    private static final Pattern POLYGON_GEOJSON_1 = Pattern.compile("type\": *\"(Multi)?Polygon");
    private static final Pattern POLYGON_GEOJSON_2 = Pattern.compile("Name\": *\"");

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        String raw = Files.readString(f.toPath());
        if(POLYGON_GEOJSON_1.matcher(raw).find() && POLYGON_GEOJSON_2.matcher(raw).find()) {
            log.info("  Generating label points from polygon centers");
            var tmp = tmpGeojson();
            Tools.mapshaper(f, tmp,
                "-filter", "Name != null",
                "-dissolve", "Name",
                "-each", "filterMinzoom="+filterMinzoom(ctx.getName())
            );
            var labelPoints = tmpGeojson();
            Tools.runAndPipeTo(labelPoints,
                "geojson-polygon-labels",
                "--precision=0.00001",
                "--label=center-of-mass",
                "--style=largest", tmp
            );

            createNewLayer(new Ctx(ctx.getName()+"_labels", ctx.getOptions(), ctx.getGeo(), labelPoints));
        }
        return f;
    }

    private String filterMinzoom(String name) {
        if(name.startsWith("borders")) return "2";
        if(name.startsWith("districts")) return "10";

        return Tools.jsPixelSizeMinzoomFunction(
            300,
            "Math.max(this.width,this.height)*"
                +Tools.jsDegToMeters("(this.bounds[1]+this.bounds[3])/2")
        );
    }
}
