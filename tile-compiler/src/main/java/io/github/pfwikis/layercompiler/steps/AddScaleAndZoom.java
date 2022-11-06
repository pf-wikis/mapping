package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import io.github.pfwikis.Tools;

public class AddScaleAndZoom extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {

        if("cities".equals(ctx.getName())) {
            var result = tmpGeojson();
            Tools.mapshaper(f, result, "-each", """
                filterMinzoom=(() => {switch(size) {
                    case 0: return 1;
                    case 1: return 2;
                    case 2: return 3;
                    default: return 3;
                }})()
            """);
            return result;
        }
        else if("locations".equals(ctx.getName())) {
            var result = tmpGeojson();
            Tools.mapshaper(f, result, "-each", "filterMinzoom=2");
            return result;
        }


        String script;
        if(Set.of("districts", "districts_label").contains(ctx.getName())) {
            script = "filterMinzoom=4";
        }
        else if("rivers".equals(ctx.getName())) {
            //set minzoom so the river is at least .25 pixels wide
            script = """
                if(!width) {
                    width=2000;
                }
                width *= $scaleFactor;
                filterMinzoom = $formula;
            """
                .replace("$scaleFactor", Tools.jsScaleFactor("(this.bounds[1]+this.bounds[3])/2"))
                .replace("$formula", Tools.jsPixelSizeMinzoomFunction(0.1, "width"));
        }
        else {
            return f;
        }
        var result = tmpGeojson();
        Tools.mapshaper(f, result, "-each", script);
        return result;
    }

}
