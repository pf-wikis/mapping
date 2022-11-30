package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.regex.Pattern;

import io.github.pfwikis.JSMath;
import io.github.pfwikis.run.Tools;
import lombok.extern.java.Log;

@Log
public class GenerateLabelCenters extends LCStep {

    private static final Pattern POLYGON_GEOJSON_1 = Pattern.compile("type\": *\"(Multi)?Polygon");
    private static final Pattern POLYGON_GEOJSON_2 = Pattern.compile("Name\": *\"");

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        String raw = new String(f);
        if(POLYGON_GEOJSON_1.matcher(raw).find() && POLYGON_GEOJSON_2.matcher(raw).find()) {
            log.info("  Generating label points from polygon centers");
            var tmp = Tools.mapshaper(f, "-filter", "Name != null");
            if(!"labels".equals(ctx.getName())) {
                tmp = Tools.mapshaper(tmp, "-dissolve", "Name");
            }
            tmp = Tools.mapshaper(tmp,
                "-each", "filterMinzoom="+filterMinzoom(ctx.getName()),
                "-each", "filterMaxzoom=4+filterMinzoom"
            );
            var labelPoints = Tools.geojsonPolygonLabels(tmp,
                "--precision=0.00001",
                "--label=center-of-mass",
                "--style=largest"
            );

            createNewLayer(new Ctx(ctx.getName()+"_labels", ctx.getOptions(), ctx.getGeo(), labelPoints));
        }
        return f;
    }

    private String filterMinzoom(String name) {
        if(name.startsWith("borders")) return "2";
        if(name.startsWith("districts")) return "10";
        if(name.startsWith("continents")) return "0";

        return JSMath.pixelSizeMinzoomFunction(
            300,
            "Math.max(this.width,this.height)*"
                +JSMath.degToMeters("(this.bounds[1]+this.bounds[3])/2")
        );
    }
}
