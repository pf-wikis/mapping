package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.Tools;

public class CreateTippecanoeProperty extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        var withProp = tmpGeojson();
        Tools.mapshaper(f, withProp,
            "-each", """
                if(!this.properties.filterMinzoom) return;
                if(!this.properties.tippecanoe) tippecanoe = {};
                if(filterMinzoom || filterMinzoom === 0) {
                    tippecanoe.minzoom = Math.max(Math.min(filterMinzoom, $maxzoom),0);
                }
            """.replace("$maxzoom", Integer.toString(ctx.getOptions().getMaxZoom()))
        );
        var result = tmpGeojson();
        Tools.runAndPipeTo(result, "jq",
            "-c", ".features[] |= ((.tippecanoe += .properties.tippecanoe) | del(.properties.tippecanoe))",
            withProp
        );
        return result;
    }

}
