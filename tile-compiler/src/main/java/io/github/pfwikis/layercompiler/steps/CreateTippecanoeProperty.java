package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class CreateTippecanoeProperty extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        var withProp = Tools.mapshaper(f,
            "-each", """
                tippecanoe = {};
                if(typeof filterMinzoom === 'number') {
                    tippecanoe.minzoom = Math.max(Math.min(filterMinzoom, $maxzoom), 0);
                }
                if(typeof filterMaxzoom === 'number') {
                    tippecanoe.maxzoom = Math.max(filterMaxzoom, 1);
                }
            """.replace("$maxzoom", Integer.toString(ctx.getOptions().getMaxZoom()))
        );
        var result = Tools.jq(withProp,
            "-c", ".features[] |= ((.tippecanoe += .properties.tippecanoe) | del(.properties.tippecanoe))"
        );
        return result;
    }

}
