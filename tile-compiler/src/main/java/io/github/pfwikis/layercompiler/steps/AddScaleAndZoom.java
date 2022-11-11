package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.Set;

import io.github.pfwikis.JSMath;
import io.github.pfwikis.run.Tools;

public class AddScaleAndZoom extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {

        if("cities".equals(ctx.getName())) {
            var result = Tools.mapshaper(f, "-each", """
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
            return Tools.mapshaper(f, "-each", "filterMinzoom=2");
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
                .replace("$scaleFactor", JSMath.scaleFactor("(this.bounds[1]+this.bounds[3])/2"))
                .replace("$formula", JSMath.pixelSizeMinzoomFunction(0.1, "width"));
        }
        else {
            return f;
        }
        return Tools.mapshaper(f, "-each", script);
    }

}
