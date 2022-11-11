package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class CreateTippecanoeProperty extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        var withProp = Tools.mapshaper(f,
            "-each", """
                if(!this.properties.filterMinzoom) return;
                if(!this.properties.tippecanoe) tippecanoe = {};
                if(filterMinzoom || filterMinzoom === 0) {
                    tippecanoe.minzoom = Math.max(Math.min(filterMinzoom, $maxzoom),0);
                }
            """.replace("$maxzoom", Integer.toString(ctx.getOptions().getMaxZoom()))
        );
        var result = Tools.jq(withProp,
            "-c", ".features[] |= ((.tippecanoe += .properties.tippecanoe) | del(.properties.tippecanoe))"
        );
        return result;
    }

}
